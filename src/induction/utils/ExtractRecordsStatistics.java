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
import induction.TreeUtils;
import induction.Utils;
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
import java.lang.String;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
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
        switch(model.getOpts().initType)
        {
            case random : model.readExamples();
                          model.init(InitType.random, opts.modelOpts.initRandom, ""); break;
            case staged : default :model.init(InitType.staged, opts.modelOpts.initRandom, "");
                          model.readExamples(); break;
        }        
        examples = new ArrayList<ExampleRecords>(model.getExamples().size());
        if(opts.predInput == null)
            parseGoldExamples();
        else
            parsePredExamples();
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
            if(documentNgrams == null) // we also need to count document ngrams to perform pruning of infrequent rules
                countDocumentNgrams();           
            try
            {
                PrintWriter out = IOUtils.openOut(Execution.getFile("recordTreebank" + (opts.binarize == Direction.left ? "Left" : "Right") + "Binarize") + opts.suffix);
                if(opts.externalTreesInput == null)
                    extractRecordTrees(out);
                else // use trees produced externally (e.g., by CCM or an RST discourse parser)
                {
                    LogInfo.logs("using external trees from file.");
                    if(opts.externalTreesInputType == ExtractRecordsStatisticsOptions.TreeType.unlabelled)
                        extractUnlabelledRecordTrees(Utils.readLines(opts.externalTreesInput), out);
                    else if(opts.externalTreesInputType == ExtractRecordsStatisticsOptions.TreeType.rst)
                        extractRstRecordTrees(Utils.readDocuments(opts.externalTreesInput), out);
                }
                out.close();
                // write trees to file
                out = IOUtils.openOut(Execution.getFile("recordTreebankRules" + (opts.binarize == Direction.left ? "Left" : "Right") + "Binarize") + opts.suffix);
                writeRules(out);
                out.close();
            }
            catch(IOException ioe)
            {
                LogInfo.error(ioe);
            }
            
        }
    }
    
    public void parseGoldExamples()
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
    
    private void parsePredExamples()
    {
        String[] preds = Utils.readLines(opts.predInput);
        int iter = 0;
        for(AExample ex: model.getExamples())
        {
            ExampleRecords er = new ExampleRecords(ex.getName());
            Example e = (Example)ex;
            int[] text = e.getText();            
            List<Integer> eventTypes = new ArrayList<Integer>();
            List<Integer> originalEventTypesIds = new ArrayList<Integer>(); // no process (e.g., don't remove duplicate events in a sentence)
//            String[] events = preds[iter++].split(" ");
//            String[] events = opts.overrideCleaningHeuristics ? preds[iter++].split(" ") : ExportExamplesToEdusFile.cleanRecordAlignments(preds[iter++].split(" "), e.getTextString());
            String[] events = opts.overrideCleaningHeuristics ? ExportExamplesToEdusFile.fixSplitSentenceAlignments(preds[iter++].split(" "), e.getTextString()): 
                    ExportExamplesToEdusFile.cleanRecordAlignments(preds[iter++].split(" "), e.getTextString());
            if(events[0].equals("not_found")) // for some reason this example doesn't have alignments; skip it
                continue;
            int[] eventIds = new int[events.length];
            for(int i = 0; i < events.length; i++)
                eventIds[i] = Integer.valueOf(events[i]);
            for(int i = 0; i < eventIds.length; i++)
            {
                int eventId = eventIds[i];
                if(eventId != -1)
                {                        
                    Object element = null;
                    try{
                    switch(opts.exportType)
                    {
                        case record : element = eventId; break;
                        default: case recordType : element = opts.useEventTypeNames ? 
                                e.events.get(eventId).getEventTypeName() : e.events.get(eventId).getEventTypeIndex(); break;
                    }
                    }catch(Exception exa){
                        System.out.println(ex.getName());
                    }
                    int indexOfElement = indexer.getIndex(element);
//                     we don't allow repetitions of record tokens in the same sentence
                    if((opts.removeRecordsWithOneWord && !recordWithOneWord(eventIds, eventId, i-1, i+1) &&
                        (eventTypes.isEmpty() || !eventTypes.contains(indexOfElement))) ||
                       eventTypes.isEmpty() || !eventTypes.contains(indexOfElement))                    
                        eventTypes.add(indexOfElement);
                    // simply add the event (not type, in case two records with the same type get concatenated). 
                    // Concatenate spans of many words.
                    if(originalEventTypesIds.isEmpty() || 
                       !(originalEventTypesIds.isEmpty() || originalEventTypesIds.get(originalEventTypesIds.size() - 1).equals(eventId)))
                    {
                        originalEventTypesIds.add(eventId);
                    }
                } // if                    
                else if(opts.extractNoneEvent)
                    eventTypes.add(indexer.getIndex(opts.useEventTypeNames ? "none" : -1));
                // default input is each clause (splitted at punctuation) goes to a seperate line
                if(!eventTypes.isEmpty() &&
                        (opts.splitClauses && Utils.isPunctuation(model.wordToString(text[i]))) ||
                        (Utils.isSentencePunctuation(model.wordToString(text[i]))))
                {                    
                    er.addSentence(new ArrayList(eventTypes));
                    eventTypes.clear();
                } // if
            } // for
            // bug fix: if the sentence doesn't end with a full stop.
            if(!eventTypes.isEmpty() && !Utils.isSentencePunctuation(model.wordToString(text[text.length - 1]))) 
            {
                er.addSentence(new ArrayList(eventTypes));
                eventTypes.clear();
            }
            // add the original sequence of event types (useful for extraction of RST trees)
            List<String> originalEventTypes = new ArrayList<String>();
            for(int i = 0; i < originalEventTypesIds.size(); i++) // HACK (overrides indexer functionality)!!!
            {
//                Object element = null;
//                switch(opts.exportType)
//                {
//                    case record : element = originalEventTypes.get(i); break;
//                    default: case recordType : element = opts.useEventTypeNames ? 
//                            e.events.get(originalEventTypes.get(i)).getEventTypeName() : e.events.get(originalEventTypes.get(i)).getEventTypeIndex(); break;
//                }
//                originalEventTypes.set(i, indexer.getIndex(element));
                originalEventTypes.add(opts.useEventTypeNames ? 
                            e.events.get(originalEventTypesIds.get(i)).getEventTypeName() : 
                            String.valueOf(e.events.get(originalEventTypesIds.get(i)).getEventTypeIndex()));
            }
            er.setOriginalSequence(originalEventTypes);
            examples.add(er);
        }
    }
    
    private boolean recordWithOneWord(int[] records, int current, int from, int to)
    {
        if(records.length == 1)
            return true;
        if(from < 0)
            return current != records[to];
        else 
        {
            if(to >= records.length)
                return current != records[from];
            else
                return !(current == records[from] || current == records[to]);
        }        
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
        int countRemoved = 0;
        for(ExampleRecords p : examples)
        {
            // exclude examples with frequency less than the threshold (useful when extracting rules from alignment input)
            if(documentNgrams.getFrequency(p.toString()) > opts.ruleCountThreshold)
            {
                // Create a flat structured tree in Penn Treebank-style. 
                // The start symbol S emits sentences in a single rule.
                // The intermediate symbol of each sentence is the aggregation of each
                // children non-terminals.
                StringBuilder str = new StringBuilder("(S "); // start symbol span
                for(Sentence sentence : p.getSentences())
                {
                    if(!sentence.isEmpty())
                        str.append(sentence.toPennTreebankStyle());
                }
                str.append(")"); // close start symbol span            
                Tree<String> tree;
                // Read the tree and binarize. We slightly tweak the naming of Xbar rules,
                // by integrating children RHS in the name of the LHS symbol.
                if(opts.modifiedBinarization)
                    tree = binarize(new PennTreeReader(new StringReader(str.toString())).next());            
                else // use the standard binarization
                {
                    Tree<String> origTree = new PennTreeReader(new StringReader(str.toString())).next();
                    tree = opts.binarize == Direction.left ? TreeUtils.leftBinarize(origTree, opts.markovOrder) : TreeUtils.rightBinarize(origTree, opts.markovOrder);
                    for (Tree<String> subtree : tree.subTreeList())
                    {
                        if(!subtree.getChildren().isEmpty())
                        {
                            String lhs = subtree.isIntermediateNode() ? String.format("[%s]", subtree.getLabelNoSpan()) : subtree.getLabelNoSpan();
                            Tree<String> ch = subtree.getChildren().get(0);
                            String rhs1 = ch.isIntermediateNode() ? String.format("[%s]", ch.getLabelNoSpan()) : ch.getLabelNoSpan();
                            if(subtree.getChildren().size() > 1)
                            {
                                ch = subtree.getChildren().get(1);
                                String rhs2 = ch.isIntermediateNode() ? String.format("[%s]", ch.getLabelNoSpan()) : ch.getLabelNoSpan();
                                rules.add(String.format("%s -> %s %s", lhs, rhs1, rhs2));
                            }
                            else if(!subtree.isPreTerminal()) // unary rules
                                rules.add(String.format("%s -> %s", lhs, rhs1));
                        } // if
                    } // for
                } // else
                out.println(p.getName());
                out.println(PennTreeRenderer.render(tree));
    //            System.out.println(PennTreeRenderer.render(tree));
            } // if
            else
                countRemoved++;
        } // for
        LogInfo.logs("Removed " + countRemoved + " examples");
    }   
    
    /**
     * Takes as input a list of unlabelled trees generated by CCM, and annotates
     * the examples with the corresponding trees using labels on non-terminals
     * based on their surface yield. 
     * @param inputTrees
     * @param out 
     */
    private void extractUnlabelledRecordTrees(String[] inputTrees, PrintWriter out)
    {
        rules = new TreeSet<String>();
        int countRemoved = 0;
        int i = 0;
        for(ExampleRecords p : examples)
        {     
            // exclude examples with frequency less than the threshold (useful when extracting rules from alignment input)
            if(documentNgrams.getFrequency(p.toString()) > opts.ruleCountThreshold)
            {
                // Read the unlabelled tree, and set the names of the non-terminals. 
                // The names of the intermediate non-terminals come from the names of the preterminals they span.
                Tree<String> tree = new PennTreeReader(new StringReader(inputTrees[i++])).next();
                tree.setLabel("S"); // set the root to the start symbol
                List<Tree<String>> subtrees = tree.subTreeList();
                for(int t = 1; t < subtrees.size(); t++)
                {
                    Tree<String> subtree = subtrees.get(t);
                    if(!subtree.getChildren().isEmpty())
                    {
                        subtree.setLabel(subtree.toSurfaceString("_")); // apply renaming                                        
                        if(subtree.isPreTerminal()) // remove leaves as they have already been promoted as the names of the preterminals 
                        {
                            subtree.getChildren().get(0).setLabel("");
                        }
                    } // if
                } // for
                // extract the rules from the tree
                for (Tree<String> subtree : tree.subTreeList())
                {
                    if(!subtree.getChildren().isEmpty())
                    {                    
                        String lhs = subtree.isIntermediateNode() ? String.format("[%s]", subtree.getLabelNoSpan()) : subtree.getLabelNoSpan();
                        Tree<String> ch = subtree.getChildren().get(0);
                        String rhs1 = ch.isIntermediateNode() ? String.format("[%s]", ch.getLabelNoSpan()) : ch.getLabelNoSpan();
                        if(subtree.getChildren().size() > 1)
                        {
                            ch = subtree.getChildren().get(1);
                            String rhs2 = ch.isIntermediateNode() ? String.format("[%s]", ch.getLabelNoSpan()) : ch.getLabelNoSpan();
                            rules.add(String.format("%s -> %s %s", lhs, rhs1, rhs2));
                        }
                        else if(!subtree.isPreTerminal()) // unary rules
                        {
                            rules.add(String.format("%s -> %s", lhs, rhs1));
                        }                                            
                    } // if
                } // for

                out.println(p.getName());
                out.println(PennTreeRenderer.render(tree));
            } // if
            else
                countRemoved++;
        } // for        
        LogInfo.logs("Removed " + countRemoved + " examples");
    }      
    
    /**
     * Takes as input a list of RST-annotated discourse trees and assigns the
     * corresponding tree for each example. Leaf nodes in the RST trees are
     * phrase segments. We map the span of these phrase segments to their corresponding
     * records, based on the input record alignments. We also append the span length
     * for each non-terminal.
     * @param inputTrees
     * @param out 
     */
    private void extractRstRecordTrees(String[] inputTrees, PrintWriter out)
    {
        rules = new TreeSet<String>();
        int countRemoved = 0;
        int i = 0;
        HistMap<String> nonTerminalsHist = new HistMap<String>();
        for(ExampleRecords p : examples)
        {     
            // exclude examples with frequency less than the threshold (useful when extracting rules from alignment input)
            if(documentNgrams.getFrequency(p.toString()) > opts.ruleCountThreshold)
            {
                // Read the rst tree                
                Tree<String> rstTree = new PennTreeReader(new StringReader(inputTrees[i++])).next();
                if(opts.countNonTerminals)
                    countNonTerminals(nonTerminalsHist, rstTree);
                if(opts.parentAnnotation)
                    parentAnnotation(rstTree);
                try
                {
                    // assign span size to the root non-terminal
                    int spanSize = rstTree.toSurfaceString().replaceAll("\\^", " ").split(" ").length;
                    rstTree.setLabel(String.format("%s[span^%s^%s]", rstTree.getLabelNoSpan(), 0, spanSize));                    
                    List<String> originalSequenceOfRecords = p.getOriginalSequence();
                    List<Tree<String>> nullPreTerminals = new ArrayList<Tree<String>>();
                    renameLabelsOfTree(rstTree, 0, new LinkedList(originalSequenceOfRecords), nullPreTerminals);
                    // HACK: in case the number of records doesn't match with the number of leaf nodes
                    // the pre-terminal will get a null label. To avoid this, we keep track of the null pre-terminals,
                    // and re-assign the label to the value of the previous pre-terminal, i.e., record.
                    // At the moment we capture such anomalies only at the end of the tree, i.e., between the last two leafs, counting left-to-right. 
                    if(!nullPreTerminals.isEmpty())
                    {
                        String lastRecord = originalSequenceOfRecords.get(originalSequenceOfRecords.size() - 1);
                        Tree<String> nullPreTerminal = nullPreTerminals.get(0);
                        nullPreTerminal.setLabel(nullPreTerminal.getLabel().replace("null", lastRecord));
                        if(nullPreTerminals.size()>1)
                            System.out.println(p.getName() + "\n" + rstTree);
                    }
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                    System.out.println("Error in: " + p.name);
                    System.out.println(rstTree);
                    System.exit(1);
                }
                Tree<String> tree = new Tree<String>("S", false); // set the root to the start symbol
                List child = new ArrayList<Tree<String>>();
                child.add(rstTree);
                tree.setChildren(child);
                if(opts.parentAnnotation)
                {
                    parentAnnotationChildren(tree);
                    parentAnnotationAppendPreTerminals(tree);
                }
//                if(opts.markovOrder >= 0)
//                    tree = opts.binarize == Direction.left ? TreeUtils.leftBinarize(tree, opts.markovOrder) : TreeUtils.rightBinarize(tree, opts.markovOrder);
                // extract the rules from the tree
                for (Tree<String> subtree : tree.subTreeList())
                {
                    if(!subtree.getChildren().isEmpty())
                    {                    
                        String lhs = subtree.isIntermediateNode() ? String.format("[%s]", subtree.getLabelNoSpan()) : subtree.getLabelNoSpan();
                        Tree<String> ch = subtree.getChildren().get(0);
                        String rhs1 = ch.isIntermediateNode() ? String.format("[%s]", ch.getLabelNoSpan()) : ch.getLabelNoSpan();
                        if(subtree.getChildren().size() > 1)
                        {
                            ch = subtree.getChildren().get(1);
                            String rhs2 = ch.isIntermediateNode() ? String.format("[%s]", ch.getLabelNoSpan()) : ch.getLabelNoSpan();
                            rules.add(String.format("%s -> %s %s", lhs, rhs1, rhs2));
                        }
                        else if(!subtree.isPreTerminal()) // unary rules
                        {
                            rules.add(String.format("%s -> %s", lhs, rhs1));
                        }                                            
                    } // if
                } // for

                out.println(p.getName());
//                System.out.println(p.getName());
                out.println(PennTreeRenderer.render(tree));                
//                System.out.println(PennTreeRenderer.render(tree));
            } // if
            else
                countRemoved++;
        } // for        
        LogInfo.logs("Removed " + countRemoved + " examples");
        if(opts.countNonTerminals)
        {
//            System.out.println(nonTerminalsHist);
            LogInfo.logs(nonTerminalsHist);
        }
    }      
    
    private void renameLabelsOfTree(Tree<String> tree, int start, Queue<String> leafs, List<Tree<String>> nullPreTerminals) throws Exception
    {
        int currentIndex = start, spanSize = 0;
        for(Tree<String> child : tree.getChildren())
        {
            if(child.isPreTerminal() && !child.getChildren().isEmpty()) // set the pre-terminal symbol to the recordType it spans
            {
                // we assume that each pre-terminal spans a segment of the document
                // that corresponds to a record. The number of segments should be the same as the number of records                
                spanSize = child.toSurfaceString().replaceAll("\\^", " ").split(" ").length;
                if(leafs.isEmpty())
                    nullPreTerminals.add(child);  
                String recordName = leafs.poll();
                String recordLabel = opts.parentAnnotation ? String.format("%s@%s", recordName, child.getLabelNoSpan().split("@")[1]) : recordName;
                child.setLabel(String.format("%s[span^%s^%s]", recordLabel, currentIndex, currentIndex + spanSize));                
//                child.setLabel(leafs.poll());                
                child.getChildren().get(0).setLabel(""); // delete the text span                    
            }
            else
            {
                // append the span length to each non-terminal
                spanSize = child.toSurfaceString().replaceAll("\\^", " ").split(" ").length;
                child.setLabel(String.format("%s[span^%s^%s]", child.getLabelNoSpan(), currentIndex, currentIndex + spanSize));
                renameLabelsOfTree(child, currentIndex,  leafs, nullPreTerminals);
                
            }
            currentIndex += spanSize;
        }
    }        
    
    private void parentAnnotation(Tree<String> tree)
    {
        // in case the node has been already annotated use only the original label
        String parentLabel = tree.getLabelNoSpan().split("\\@")[0];
        for(Tree<String> child : tree.getChildren())
        {
            if(!child.isLeaf())
            {
                child.setLabel(String.format("%s@%s", child.getLabelNoSpan(), parentLabel));
                parentAnnotation(child);
            }            
        }
    }
    
    private void parentAnnotationAppendPreTerminals(Tree<String> tree)
    {
        for(Tree<String> child : tree.getChildren())
        {
            if(child.isPreTerminal() && !child.getChildren().isEmpty() && child.getLabel().contains("@"))
            {
                String name = child.getLabelNoSpan().split("@")[0];
                Tree<String> terminal = new Tree<String>(name, false);
                List<Tree<String>> childTemp = new ArrayList<Tree<String>>();
                childTemp.add(new Tree<String>("", false));
                terminal.setChildren(childTemp);
                List<Tree<String>> termTemp = new ArrayList<Tree<String>>();
                termTemp.add(terminal);
                child.setChildren(termTemp);
            }    
            parentAnnotationAppendPreTerminals(child);
        }
    }
    
    private void parentAnnotationChildren(Tree<String> tree)
    {
        // in case the node has been already annotated use only the original label
        String parentLabel = tree.getLabelNoSpan().split("\\@")[0];
        for(Tree<String> child : tree.getChildren())
        {
            if(!child.isLeaf())
            {
                child.setLabel(String.format("%s@%s", child.getLabelNoSpan(), parentLabel));                
            }            
        }
    }
    
    private void countNonTerminals(HistMap map, Tree<String> tree)
    {
        map.add(tree.getLabel());
        for(Tree child : tree.getChildren())
        {
            if(!child.isLeaf())
                countNonTerminals(map, child);
        }
    }
    
    public Tree<String> binarize(Tree<String> tree) 
    {
        String rootLabel = tree.getLabelNoSpan();

        if (tree.getChildren().isEmpty())
        {
            return tree;
        }
        if (tree.getChildren().size() == 1)
        {
            Tree<String> child = tree.getChildren().get(0);
            if(!tree.isPreTerminal())
                rules.add(String.format("%s -> %s", rootLabel, child.getLabelNoSpan()));
            return new Tree<String>(rootLabel,
                    tree.isIntermediateNode(),
                    ListUtils.newList(binarize(child)));
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
                        (i == newChildren.size() - 1 ? rootLabel : (newTree.getLabelNoSpan() + "_" + newChildren.get(i).getLabelNoSpan())).replaceAll("SENT-", "");
                rules.add(String.format("%s -> %s %s", intermediateLabel, newTree.getLabelNoSpan(), newChildren.get(i).getLabelNoSpan()));
                newTree = new Tree<String>(intermediateLabel, false, ListUtils.newList(newTree, newChildren.get(i)));            
            }
            return newTree;
        } // if
        else
        {
            Tree<String> newTree = newChildren.get(newChildren.size()-1);
            for(int i = newChildren.size()-2; i >= 0; i--) 
            {
              String intermediateLabel = (i == 0 ? rootLabel : (newChildren.get(i).getLabelNoSpan() + "_" + newTree.getLabelNoSpan())).replaceAll("SENT-", "");              
              rules.add(String.format("%s -> %s %s", intermediateLabel, newChildren.get(i).getLabelNoSpan(), newTree.getLabelNoSpan()));
              newTree = new Tree<String>(intermediateLabel, false, ListUtils.newList(newChildren.get(i), newTree));
            }
            return newTree;
        }
        
    }      
    
    public void testExecute()
    {
        model = new GenerativeEvent3Model(opts.modelOpts);
        switch(model.getOpts().initType)
        {
            case random : model.readExamples();
                          model.init(InitType.random, opts.modelOpts.initRandom, ""); break;
            case staged : default :model.init(InitType.staged, opts.modelOpts.initRandom, "");
                          model.readExamples(); break;
        }
        model.readExamples();
        examples = new ArrayList<ExampleRecords>(model.getExamples().size());
        if(opts.predInput == null)
            parseGoldExamples();
        else
            parsePredExamples();
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
            if(documentNgrams == null) // we also need to count document ngrams to perform pruning of infrequent rules
                countDocumentNgrams();
            if(opts.externalTreesInput == null) // compute ngrams
            {
                extractRecordTrees(new PrintWriter(System.out));           
            }
            else
            {
                if(opts.externalTreesInputType == ExtractRecordsStatisticsOptions.TreeType.unlabelled)
                    extractUnlabelledRecordTrees(Utils.readLines(opts.externalTreesInput), new PrintWriter(System.out));
                else if(opts.externalTreesInputType == ExtractRecordsStatisticsOptions.TreeType.rst)
                    extractRstRecordTrees(Utils.readDocuments(opts.externalTreesInput), new PrintWriter(System.out));
            }
            writeRules(new PrintWriter(System.out));
        }
    }
    
    class ExampleRecords
    {
        private String name;
        private List<Sentence> sentences;
        private List<String> originalSequenceOfRecordTypes;
        
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

        private void setOriginalSequence(List<String> originalRecordTypes)
        {
            this.originalSequenceOfRecordTypes = originalRecordTypes;
        }
        
        protected List<String> getOriginalSequence()
        {
//            List<String> list = new ArrayList<String>();
//            for(Integer id : originalSequenceOfRecordTypes)
//                list.add((String)indexer.getObject(id));
//            return list;
            return originalSequenceOfRecordTypes;
        }
        
        protected List getPermutation()
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
        
        public String documentToString(boolean delimitSentences)
        {
            StringBuilder str = new StringBuilder();            
            for(Sentence sentence : sentences)
            {                
                str.append(sentence).append(" ");
                if(delimitSentences)
                    str.append("| ");
            }
            return str.toString().trim();
        }
        
        @Override
        public String toString()
        {
            return opts.exportEvent3 ? String.format("$NAME\n%s\n$TEXT\n%s", name, 
                    documentToString(opts.delimitSentences)) : documentToString(opts.delimitSentences);
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

        public boolean isEmpty()
        {
            return size == 0;
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
            if(tokens.size() == 1) // make lhs of unary rules unique
                intermediateLabel.insert(0, "SENT-");
            try{
            str.insert(0, "(" + intermediateLabel.substring(0, intermediateLabel.length() - 1) + " ");
            }catch(Exception e)
            {
                e.printStackTrace();
            }
            str.append(")");

            
            return str.toString();
        }
        
        @Override
        public String toString()
        {
            StringBuilder str = new StringBuilder();
            for(Integer r : tokens)
                str.append(indexer.getObject(r)).append(" ");
            return str.toString().trim();
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
