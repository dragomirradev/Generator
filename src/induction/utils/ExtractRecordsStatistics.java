package induction.utils;

import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.ling.Trees.PennTreeReader;
import edu.berkeley.nlp.ling.Trees.PennTreeRenderer;
import fig.basic.IOUtils;
import fig.basic.Indexer;
import fig.basic.ListUtils;
import fig.basic.LogInfo;
import fig.exec.Execution;
import induction.Options.InitType;
import induction.problem.AExample;
import induction.problem.event3.Event3Model;
import induction.problem.event3.Example;
import induction.problem.event3.Widget;
import induction.problem.event3.generative.GenerativeEvent3Model;
import induction.utils.ExtractRecordsStatisticsOptions.Direction;
import induction.utils.HistMap.Counter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author konstas
 */
public class ExtractRecordsStatistics
{
    ExtractRecordsStatisticsOptions opts;    
    Event3Model model;
    List<ExampleRecords> examples;
    HistMap repeatedRecords;
    HistMap<Sentence> sentenceNgrams;
    HistMap<String> documentNgrams;
    Set<String> rules;
    Indexer indexer = new Indexer();
    
    public ExtractRecordsStatistics(ExtractRecordsStatisticsOptions opts)
    {
        this.opts = opts;
    }                
    
    public void execute()
    {
        model = new GenerativeEvent3Model(opts.modelOpts);
        model.init(InitType.staged, opts.modelOpts.initRandom, "");
        model.readExamples();
        examples = new ArrayList<ExampleRecords>(model.getExamples().size());
        parseExamples();
        if(opts.writePermutations)
        {
            LogInfo.logs("Writing permutations...");
            writePermutations();
        }
        if(opts.countRepeatedRecords)
        {
            LogInfo.logs("Count repeated records in a permutation...");
            countRepeatedRecords();
            writeObject(repeatedRecords, "repeatedRecords");
        }
        if(opts.countSentenceNgrams)
        {
            LogInfo.logs("Count ngrams in each sentence...");
            countSentenceNgrams();
            writeObject(sentenceNgrams, "sentenceNgrams");
        }
        if(opts.countDocumentNgrams)
        {
            LogInfo.logs("Count ngrams in each document...");
            countDocumentNgrams();
            writeObject(documentNgrams, "documentNgrams");
        }
        if(opts.extractRecordTrees)
        {
            LogInfo.logs("Extracting record trees...");
            if(sentenceNgrams == null) // we need to compute sentence ngrams to construct CFG rules that bias toward sentence constituents
                countSentenceNgrams();
            try
            {
                PrintWriter out = IOUtils.openOut(Execution.getFile("recordTreebank" + (opts.binarize == Direction.left ? "Left" : "Right") + "Binarize"));
                extractRecordTrees(out);
                out.close();
                out = IOUtils.openOut(Execution.getFile("recordTreebankRules" + (opts.binarize == Direction.left ? "Left" : "Right") + "Binarize"));
                writeRules(out);
                out.close();
            }
            catch(IOException ioe)
            {
                LogInfo.error(ioe);
            }
            
        }
    }
    
    public void parseExamples()
    {       
        for(AExample ex: model.getExamples())
        {
            ExampleRecords er = new ExampleRecords(ex.getName());
            Example e = (Example)ex;
            int[] text = e.getText();
            int[] startIndices = e.getStartIndices();
            Widget w = e.getTrueWidget();
            List<Integer> eventTypes = new ArrayList<Integer>();
            for(int i = 1; i < startIndices.length; i++)
            {                
                for(int[] events : w.getEvents())
                {
                    int eventId = events[startIndices[i-1]];
                    if(eventId != -1)
                    {                        
                        Object element = null;
                        switch(opts.exportType)
                        {
                            case record : element = eventId; break;
                            default: case recordType : element = opts.useEventTypeNames ? 
                                    e.events.get(eventId).getEventTypeName() : e.events.get(eventId).getEventTypeIndex(); break;
                        }
                        // collapse consecutive records having the same type                        
//                        if(eventTypes.isEmpty() || eventType != eventTypes.get(eventTypes.size() - 1))
                        int indexOfElement = indexer.getIndex(element);
                        // we don't allow repetitions of record tokens in the same sentence
                        if(eventTypes.isEmpty() || !eventTypes.contains(indexOfElement))
                            eventTypes.add(indexOfElement);
                    } // if                    
                }
                if(opts.extractNoneEvent && w.getEvents()[0][startIndices[i-1]] == -1)
                    eventTypes.add(indexer.getIndex(opts.useEventTypeNames ? "none" : -1));
                // default input is each clause (splitted at punctuation) goes to a seperate line
                if(!eventTypes.isEmpty() && (opts.splitClauses || endOfSentence(model.wordToString(text[startIndices[i]-1]))))
                {
                    er.addSentence(new ArrayList(eventTypes));
                    eventTypes.clear();
                } // if
            } // for
            examples.add(er);
        } // for
    }
    
    private boolean endOfSentence(String token)
    {
        return token.equals(".") || token.equals("./.") || token.equals(":") || token.equals("--/:");
    }
      
    private void writePermutations()
    {
        try
        {
            PrintWriter out = IOUtils.openOut(Execution.getFile("permutations"));
            for(ExampleRecords p : examples)
                out.println(p);
            out.close();
        } catch (IOException ex)
        {
            LogInfo.error(ex);
        } catch (NullPointerException ex)
        {
            LogInfo.error(ex);
        }        
    }
    
    private void writeRules(PrintWriter out)
    {
        for(String rule : rules.toArray(new String[0]))
            out.println(rule);
        out.close();      
    }
    
    private void countRepeatedRecords()
    {
        repeatedRecords = new HistMap<Integer>();
        for(ExampleRecords er : examples)
        {
            HistMap<Object> repRecsInSent = new HistMap<Object>();
            for(Object i : er.getPermutation())
                repRecsInSent.add(i);            
            for(Entry e : repRecsInSent.getEntries())
            {
                if(((Counter)e.getValue()).getValue() > 1)
                    repeatedRecords.add(((Integer)e.getKey()));
            }
        } // for
    }
    
    private void countSentenceNgrams()
    {
        sentenceNgrams = new HistMap<Sentence>();
        for(ExampleRecords er : examples)
        {
            for(Sentence sentence : er.getSentences())
                sentenceNgrams.add(sentence);
//            for(int i = 0; i < er.numberOfSentences(); i++)
//                sentenceNgrams.add(er.sentenceToString(i));
        } // for
    }
    
    private void countDocumentNgrams()
    {        
        documentNgrams = new HistMap<String>();        
        for(ExampleRecords er : examples)
        {
            documentNgrams.add(er.toString());            
        } // for
    }
    
    private void writeObject(Object obj, String filename)
    {
        try
        {
            PrintWriter out = IOUtils.openOut(Execution.getFile(filename));
            out.print(obj);
            out.close();
        }
        catch(Exception e)
        {
            LogInfo.error(e);
        }
    }
    
    private void extractRecordTrees(PrintWriter out)
    {
        rules = new TreeSet<String>();
                              
        for(ExampleRecords p : examples)
        {
            // Create a flat structured tree in Penn Treebank-style. 
            // The start symbol S emits sentences in a single rule.
            // The intermediate symbol of each sentence is the aggregation of each
            // children non-terminals.
            StringBuilder str = new StringBuilder("(S "); // start symbol span
            for(Sentence sentence : p.getSentences())
            {
                str.append(sentence.toPennTreebankStyle());
            }
            str.append(")"); // close start symbol span
            // Read the tree and binarize. We slightly tweak the naming of Xbar rules,
            // by integrating children RHS in the name of the LHS symbol.
            Tree<String> tree = binarize(new PennTreeReader(new StringReader(str.toString())).next());
            out.println(p.getName());
            out.println(PennTreeRenderer.render(tree));
//            System.out.println(PennTreeRenderer.render(tree));
        } // for
    }      
    
    public Tree<String> binarize(Tree<String> tree) 
    {
        String rootLabel = tree.getLabel();

        if (tree.getChildren().isEmpty())
        {
            return tree;
        }
        if (tree.getChildren().size() == 1)
        {
            return new Tree<String>(rootLabel,
                    tree.isIntermediateNode(),
                    ListUtils.newList(binarize(tree.getChildren().get(0))));
        }

        // Binarize all children
        List<Tree<String>> newChildren = new ArrayList();
        for (Tree<String> child : tree.getChildren())
        {
            newChildren.add(binarize(child));
        }

        // Build tree with intermediate nodes
        if(opts.binarize == Direction.left)
        {
            Tree<String> newTree = newChildren.get(0);
            for (int i = 1; i < newChildren.size(); i++)
            {
                String intermediateLabel =
                        i == newChildren.size() - 1 ? rootLabel : (newTree.getLabel() + "_" + newChildren.get(i).getLabel());
                rules.add(String.format("%s -> %s %s", intermediateLabel, newTree.getLabel(), newChildren.get(i).getLabel()));
                newTree = new Tree<String>(intermediateLabel, false, ListUtils.newList(newTree, newChildren.get(i)));            
            }
            return newTree;
        } // if
        else
        {
            Tree<String> newTree = newChildren.get(newChildren.size()-1);
            for(int i = newChildren.size()-2; i >= 0; i--) 
            {
              String intermediateLabel = i == 0 ? rootLabel : (newChildren.get(i).getLabel() + "_" + newTree.getLabel());
              rules.add(String.format("%s -> %s %s", intermediateLabel, newChildren.get(i).getLabel(), newTree.getLabel()));
              newTree = new Tree<String>(intermediateLabel, false, ListUtils.newList(newChildren.get(i), newTree));
            }
            return newTree;
        }
        
    }      
    
    public void testExecute()
    {
        model = new GenerativeEvent3Model(opts.modelOpts);
        model.init(InitType.staged, opts.modelOpts.initRandom, "");
        model.readExamples();
        examples = new ArrayList<ExampleRecords>(model.getExamples().size());
        parseExamples();
        if(opts.writePermutations)
            for(ExampleRecords p : examples)
            {
                System.out.println(p);
            }
        if(opts.countRepeatedRecords)
        {
            countRepeatedRecords();
            System.out.println(repeatedRecords);
        }
        if(opts.countSentenceNgrams)
        {
            countSentenceNgrams();
            System.out.println(sentenceNgrams);
        }
        if(opts.countDocumentNgrams)
        {
            countDocumentNgrams();
            System.out.println(documentNgrams);
        }
        if(opts.extractRecordTrees)
        {
            LogInfo.logs("Extracting record trees...");
            if(sentenceNgrams == null) // we need to compute sentence ngrams to construct CFG rules that bias toward sentence constituents
                countSentenceNgrams();
            extractRecordTrees(new PrintWriter(System.out));
            writeRules(new PrintWriter(System.out));
        }
    }
    
    class ExampleRecords
    {
        private String name;
        private List<Sentence> sentences;

        public ExampleRecords(String name)
        {
            this.name = name;
            sentences = new ArrayList<Sentence>();
        }

        public String getName()
        {
            return name;
        }
                       
        void addSentence(List types)
        {
            sentences.add(new Sentence(types));
        }

        List getPermutation()
        {
            List permutation = new ArrayList();
            for(Sentence sentence : sentences)
            {                
                    permutation.addAll(sentence.getTokens());
            }
            return permutation;
        }
        
        int numberOfSentences()
        {
            return sentences.size();
        }

        int getSize()
        {
            int size = 0;
            for(Sentence sentence : sentences)
                size += sentence.getSize();
            return size;
        }
        
        public List<Sentence> getSentences()
        {
            return sentences;
        }
                
        String sentenceToString(int i)
        {
            assert i < sentences.size();            
            return sentences.get(i).toString();
        }
        
        @Override
        public String toString()
        {
            StringBuilder str = new StringBuilder();
            for(Sentence sentence : sentences)
            {                
                str.append(sentence).append(" ");
                if(opts.delimitSentences)
                    str.append("| ");
            }
            return str.toString().trim();
        }        
    }
    
    class Sentence
    {
        private List<Integer> tokens;
        private int size;
        
        public Sentence(List types)
        {
            this.tokens = types;
            this.size = types.size();
        }

        public int getSize()
        {
            return size;
        }

        public List<Integer> getTokens()
        {
            return tokens;
        }

        public List<Integer> getFragment(int i, int j)
        {
            assert i > 0 && j < size && i < j;
            return tokens.subList(i, j+1);
        }
        
        public String generateIntermediateLabel()
        {
            StringBuilder str = new StringBuilder();
            for(Integer r : tokens)
                str.append(indexer.getObject(r)).append("-");
            return str.toString();
        }
        
        public String toPennTreebankStyle()
        {
            StringBuilder intermediateLabel = new StringBuilder();
            StringBuilder str = new StringBuilder();
            for(Integer r : tokens)
            {
                String tok = String.valueOf(indexer.getObject(r));
                str.append("(").append(tok).append(") ");
                intermediateLabel.append(tok).append("_");
            }
            if(tokens.size() > 1)
            {
                str.insert(0, "(" + intermediateLabel.substring(0, intermediateLabel.length() - 1) + " ");
                str.append(")");
            }
            
            return str.toString();
        }
        
        @Override
        public String toString()
        {
            StringBuilder str = new StringBuilder();
            for(Integer r : tokens)
                str.append(indexer.getObject(r)).append(" ");
            return str.toString();
        }

        @Override
        public boolean equals(Object obj)
        {
            assert obj instanceof Sentence;
            return this.tokens.equals(((Sentence)obj).tokens);
        }

        @Override
        public int hashCode()
        {
            int hash = 3;
            hash = 29 * hash + (this.tokens != null ? this.tokens.hashCode() : 0);
            return hash;
        }                
    }
}
