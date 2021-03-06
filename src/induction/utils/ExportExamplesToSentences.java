package induction.utils;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import induction.Utils;
import induction.problem.event3.Event3Example;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses an input list or path of text files and outputs each to a given output
 * file, each text entry per line, with <code>ngramSize</code> <s> at the
 * beginning, and </s> at the end. All instances of numbers may be replaced with
 * the keyword
 * <num>
 *
 * @author konstas
 */
public class ExportExamplesToSentences {

    String target, source, HEADER = "", fileExtension;
    int ngramSize;
    BufferedOutputStream bos;
    TokenizerFactory<Word> tokenizer;
    private final MaxentTagger tagger;
    String tagDelimiter;
    final String SOS = "<s>", EOS = "</s>";

    public enum SourceType {
        PATH, LIST, FILE
    };
    SourceType type;
    boolean replaceNumbers, toLowerCase, stripWords;

    public ExportExamplesToSentences(String targetFile, String sourceDir, int ngramSize,
            SourceType type, String fileExtension,
            boolean replaceNumbers, boolean toLowerCase,
            boolean stripWords, String tagDelimiter) {
        this.target = targetFile;
        this.source = sourceDir;
        this.ngramSize = ngramSize;
        this.tokenizer = PTBTokenizer.factory();
        this.tagger = new MaxentTagger(MaxentTagger.DEFAULT_JAR_PATH);
        this.type = type;
        this.fileExtension = fileExtension;
        this.replaceNumbers = replaceNumbers;
        this.toLowerCase = toLowerCase;
        this.stripWords = stripWords;
        this.tagDelimiter = tagDelimiter;
    }

    public void execute(boolean tokeniseOnly) {
        try {
            for (int i = 0; i < ngramSize - 1; i++) {
                HEADER += SOS + " ";
            }
            bos = new BufferedOutputStream(new FileOutputStream(target));
            if (tokeniseOnly) {
                tokeniseSource();
                computeCorpusStatistics();
            } else if (type == SourceType.PATH) {
                addPath(source);
            } else if (type == SourceType.LIST) {
                addList(source);
            } else {
                processExamplesInSingleFile(source);
            }
            bos.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Parses a file that contains a list of the files to be processed
     *
     * @param path
     */
    private void addList(String path) {
        for (String line : Utils.readLines(path)) {
            addPath(line);
        } // for
    }

    /**
     * Recursively processes files under the <code>path</code> directory
     *
     * @param path
     */
    private void addPath(String path) {
        File file = new File(path);
        if (file.isDirectory()) {
            for (String fileStr : Utils.sortWithEmbeddedInt(file.list())) {
                addPath(path + "/" + fileStr);
            } // for
        } // if
        else {
            if (!file.getName().endsWith(fileExtension)) {
                path = IOUtils.stripFileExt(path) + "." + fileExtension;
            }
            processFile(path);
        }
    }

    private void processExamplesInSingleFile(String source) {
        List<Event3Example> examples = Utils.readEvent3Examples(source, true);
        for (Event3Example ex : examples) {
            try {
                writeToFile(processExample(ex.getText()));
            } catch (IOException ioe) {
                System.err.println(ioe.getMessage());
            }
        }
//        if(new File(source).exists())
//        {
//            String key = null;
//            StringBuilder str = new StringBuilder();
//            for(String line : Utils.readLines(source))
//            {
//                if(line.startsWith("Example_") || line.equals("$NAME"))
//                {
//                    if(key != null) // only for the first example
//                    {                      
//                        processEventExample(str.toString());
//                        str = new StringBuilder();
//                    }
//                    key = line;
//                } // if                   
//                str.append(line).append("\n");
//            }  // for           
//            processEventExample(str.toString()); // don't forget last example
//        }
    }

    protected void processFile(String path) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(path));
            String line = "", textInOneLine = "";
            while ((line = br.readLine()) != null) {
                textInOneLine += line + " ";
            }
            br.close();
            writeToFile(processExample(textInOneLine));
        } catch (IOException ioe) {
            LogInfo.error("Error reading file " + path);
        }
    }

    protected void processEventExample(String input) {
        try {
            writeToFile(processExample(Utils.extractExampleFromString(input)[1]));
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        }
    }

    protected String processExample(String input) {
        StringBuilder textOut = new StringBuilder(HEADER);
        for (String s : input.trim().split(" ")) {
            String word = stripWords ? Utils.stripWord(s, true, tagDelimiter) : s;
            textOut.append((replaceNumbers && (word.matches("-\\p{Digit}+|"
                    + // negative numbers
                    "-?\\p{Digit}+\\.\\p{Digit}+")
                    || // decimals
                    (word.matches("\\p{Digit}+")
                    && // numbers
                    !(word.contains("am") || word.contains("pm")))) // but not hours!
                    ) ? "<num> " : word).append(" ");
        }
        return textOut.toString();
    }

    protected void writeToFile(String input) throws IOException {
        bos.write(((toLowerCase ? input.trim().toLowerCase() : input.trim())
                + " " + EOS + "\n").getBytes());
    }

    /**
     * Tokenises source file. Assumes <s> and </s> are already placed in source
     */
    public void tokeniseSource() {
        try {
            BufferedReader br = new BufferedReader(new FileReader(source));
            String line = "", out = "";
            while ((line = br.readLine()) != null) {
                out = "";
//                for(String s : tokenizer.tokenize(line.substring(HEADER.length(), line.length() - 5))) // ignore <s>'s and </s>
//                for(Word w : tokenizer.getTokenizer(new StringReader(line.substring(HEADER.length(), line.length() - 5))).tokenize()) // ignore <s>'s and </s>
                for (Word w : tokenizer.getTokenizer(new StringReader(line.contains("<s>")
                        ? line.substring(HEADER.length(), line.length() - 5)
                        : line)).tokenize()) // ignore <s>'s and </s>
                {
                    String s = w.word();
                    // tokenisation might give numbers not found previously
                    out += (replaceNumbers && (s.matches("-\\p{Digit}+|"
                            + // negative numbers
                            "-?\\p{Digit}+\\.\\p{Digit}+")
                            || // decimals
                            s.matches("-?\\p{Digit}+,\\p{Digit}+")
                            || // decimals
                            (s.matches("\\p{Digit}+")
                            && // numbers
                            !(s.contains("am") || s.contains("pm")))) // but not hours!
                            ) ? "<num> " : s + " ";
                }
                bos.write((HEADER + out + " " + EOS + "\n").getBytes());
            }
            br.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(1);
        }
    }

    public void computeCorpusStatistics() {
        Set<String> stopWords = new HashSet<>(Arrays.asList(Utils.readLines("lib/stopwords.txt")));
        List<String> lines = Arrays.asList(Utils.readLines(source));
        HistMap<String> unigrams = new HistMap<>();
        HistMap<String> unigramsTyped = new HistMap<>();
        HistMap<String> pos = new HistMap<>();
        for (String line : lines) {
            List<Word> tokens = tokenizer.getTokenizer(new StringReader(line)).tokenize();            
            tokens.stream()
                    .filter(word -> !stopWords.contains(word.word().toLowerCase()))
                    .forEach(word -> unigrams.add(word.word().toLowerCase()));
            tagger.tagSentence(tokens).stream()
                    .filter(word -> !stopWords.contains(word.word().toLowerCase()))
                    .map(tagged -> {
                        unigramsTyped.add(String.format("%s/%s", tagged.tag().equals("NNP") ? tagged.word() : tagged.word().toLowerCase(), tagged.tag()));
                        return tagged.tag();
                    })
                    .forEach(pos::add);
        }        
        Utils.write(String.format("%s.unigrams", source), unigrams.toString());
        Utils.write(String.format("%s.unigramsTyped", source), unigramsTyped.toString());
        Utils.write(String.format("%s.pos", source), pos.toString());
    }

    public static void main(String[] args) {
        //ATIS
//        String source = "/home/konstas/EDI/candc/candc-1.00/atis5000.sents.full.tagged.CDnumbers";
//        String target = "/home/konstas/EDI/candc/candc-1.00/atis5000.sents.full.tagged.CDnumbers.tags_only.sentences";
        //WEATHERGOV
//        String source = "gaborLists/trainListPathsGabor";
//        String target = "weatherGovLM/gabor-srilm-abs-3-gram.model.tagged.sentences";
        //ROBOCUP
//        String source = "robocupLists/robocupAllPathsTrain";
//        String target = "robocupLM/robocup-all-3-gram.tagged.sentences";        
        //WINHELP     
//        String source = "data/branavan/winHelpHLA/winHelpRL.sents.all";
//        String target = "data/branavan/winHelpHLA/winHelpRL-split-3-gram.sentences";        
        //AMR-LDC
//        String source = "../hackathon/data/ldc/split/training/training-thres-5.event3";
//        String target = "../hackathon/data/ldc/split/training/training-3-gram.sentences";
        //ROC-Stories
        String source = "/Users/ikonstas/data/ROC_stories/short_stories_dataset_v3_sentence_only.txt";
        String target = "/Users/ikonstas/data/ROC_stories/short_stories_dataset_v3_sentence_only.txt.tok";
        boolean tokeniseOnly = true, replaceNumbers = false, toLowerCase = false, stripWords = false;
        int ngramSize = 5;
        int folds = 1;
        for (int i = 1; i <= folds; i++) {
            String tagDelimiter = "_";
            // FOLDS
//            String source = "data/branavan/winHelpHLA/folds/winHelpFold"+i+"PathsTrain";
//            String target = "winHelpLM/winHelpRL-split-fold"+i+"-3-gram.sentences";            

            String fileExtension = "text.tagged";
            ExportExamplesToSentences lmp = new ExportExamplesToSentences(target, source, ngramSize,
                    SourceType.FILE, fileExtension,
                    replaceNumbers, toLowerCase, stripWords, tagDelimiter);
            lmp.execute(tokeniseOnly);
        }
    }
}
