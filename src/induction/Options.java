package induction;

import java.util.*;
import fig.basic.*;

public class Options {
  public enum ModelType { gmm, pmmm, hmm, pcfg, dmv, seg, align, event3, generate };
  public enum InitType { random, bait, supervised, uniformz, artificial, staged };
  public enum InputFormat { raw, tag, mrg, seg };
  public enum AlignmentModel { m1, m2, hmm };
  public enum NgramWrapper {kylm, srilm, roark};
  public enum ReorderType {event, eventType};
  // Input
  @Option public ArrayList<String> inputPaths = new ArrayList();
  @Option public ArrayList<String> inputLists = new ArrayList();
  @Option public ArrayList<String> testInputPaths = new ArrayList();
  @Option public ArrayList<String> testInputLists = new ArrayList();
  @Option public String inputFileExt;
  @Option public String inputFileExt2; // For word alignment, the extension of the other language
  @Option(gloss="Description file for initializing artificial parameters") public String artificialDescriptionPath;
  @Option(gloss="Format of input") public InputFormat inputFormat = InputFormat.raw;
  @Option(gloss="Maximum number of examples") public int maxExamples = Integer.MAX_VALUE;
  @Option(gloss="Maximum number of test examples") public int testMaxExamples = Integer.MAX_VALUE;
  @Option(gloss="Maximum length of an example") public int maxExampleLength = Integer.MAX_VALUE;
  @Option(gloss="For parsing") public boolean useTagsAsWords = false;
  @Option(gloss="Segment characters (default is segmenting words)") public boolean segChars = false;
  @Option(gloss="For segmentation") public String segBoundary = "||";
  @Option(gloss="Penalize words") public double segPenalty = 0.5;
  @Option(gloss="Maximum number of examples to use for extracting phrases (to limit # phrases)") public int maxExamplesForPhrases = Integer.MAX_VALUE;

  // Model
  @Option(gloss="Model", required=true) public ModelType modelType;
  @Option(gloss="Number of hidden states") public int K = 5;
  @Option(gloss="Maximum phrase length") public int maxPhraseLength = 5;
  @Option public AlignmentModel alignmentModel = AlignmentModel.m1;
  @Option(gloss="Threshold for posterior decoding") public double posteriorThreshold = 0.5;
  @Option public double gmmVariance = 1;
  @Option public double gmmGenRange = 10;
  @Option(gloss="PCFGs: tree structure is fixed") public boolean fixBracketing = false;
  @Option(gloss="PCFGs: fix the preterminal (POS) tags") public boolean fixPreTags = false;
  @Option(gloss="PCFGs: collapse the nonterminal tag set") public boolean collapseNonTags = false;
  @Option(gloss="PCFGs: number of hidden preterminal states") public int pK = 5;
  @Option(gloss="PCFGs: add CCM potentials (generate content and context)") public boolean useCCMPotentials = false;
  @Option(gloss="PCFGs: have the CCM generation depend on the state") public boolean ccmDependsOnState = false;
  @Option public boolean removePunctuation = false;
  @Option public boolean alignAgreement = true;

  //// Event3 model
  @Option(gloss="Output for external evaluation script") public boolean fullPredForEval = false;
  @Option(gloss="Random matching - baseline") public boolean fullPredRandomBaseline = false;
  @Option public Random fullPredRandom = new Random(1);

  @Option public String[] excludedFields = new String[0]; // List of <event type name>.<field name>
  @Option public String[] excludedEventTypes = new String[0]; // List of <event type name>
  @Option(gloss="Take the first event of each type (don't do this)") public boolean takeOneOfEventType = false;
  @Option public boolean treatCatAsSym = false;
  @Option public boolean useOnlyLabeledExamples = false;

  // Changes to the model can happen during training, so each of these specifies a starting and ending iteration for the
  // corresponding flag to be on
  @Option public Pair<Integer,Integer> indepEventTypes = new Pair(0, 0);
  @Option public Pair<Integer,Integer> indepFields = new Pair(0, 0);
  @Option(gloss="Each word chooses event type/field independently (word alignment, no segmentation)")
    public Pair<Integer,Integer> newEventTypeFieldPerWord = new Pair(0, 0);
  @Option(gloss="Each word chooses field independently (no segmentation at the field level)")
    public Pair<Integer,Integer> newFieldPerWord = new Pair(0, 0);
  
  @Option public Pair<Integer,Integer> oneEventPerExample = new Pair(0, 0);
  @Option public Pair<Integer,Integer> oneFieldPerEvent = new Pair(0, 0);

  @Option(gloss="p(t | w)") public boolean includeEventTypeGivenWord = false;
  @Option(gloss="Allow a field to show up twice in a row") public boolean disallowConsecutiveRepeatFields = true;

  // At event-type level
  @Option(gloss="p(include event? | e, v)") public boolean useEventSalienceModel = false;
  @Option(gloss="p(t) or p(t | t0)") public boolean useEventTypeDistrib = true;

  // At field level
  @Option(gloss="Generate and constrain the set of fields which are used for each event mentioned on these event types")
    public String[] useFieldSetsOnEventTypes = new String[0];
  @Option public Pair<Integer,Integer> useFieldSets = new Pair(0, 0);
  @Option public int minFieldSetSize = 0;
  @Option public int maxFieldSetSize = Integer.MAX_VALUE;

  // Tracks
  @Option(gloss="Tracks (for NFL): each track gets a subset of the event types")
    public String[] eventTypeTracks = new String[] { "ALL" }; // Default is one track with everything
  @Option(gloss="Jointly decide whether to have non-none event") public boolean jointEventTypeDecision = false;

  // Modify
  @Option public boolean includeEventTypeAsSymbol = false;
  @Option(gloss="Stem symbols, strings, and words") public static boolean stemAll = false;
  @Option(gloss="Annotate and generate numeric quantities with labels (the word that follows)") public Pair<Integer,Integer> genLabels = new Pair(0, 0);

  @Option public String wordRolesPath;
  @Option(gloss="List of <event type name>.<field name>") public String[] useWordRolesOnFields = new String[0]; 

  // Specific control ofsmoothing
  @Option public double noneEventTypeSmoothing = 0;
  @Option public double fixedNoneEventTypeProb = Double.NaN;
  @Option public double fixedGenericProb = Double.NaN;
  @Option public double noneFieldSmoothing = 0;
  @Option public double fieldNameSmoothing = 0;
  @Option public boolean discountCatEmissions = false;

  @Option public boolean andIsPunctuation = false;
  @Option(gloss="A entity segment only break on punctuation") public boolean onlyBreakOnPunctuation = false;
  @Option(gloss="A entity segment cannot cross punctuation") public boolean dontCrossPunctuation = false;
  @Option(gloss="Limit field length (e.g. num and sym must be 1)") public boolean limitFieldLength = false;
  @Option(gloss="For each line, make a separate example (NFL data)") public boolean oneExamplePerLine = false;

  // Generation
  @Option(gloss="Average text length (default=29 for weather-data)") public int averageTextLength = 29;
  @Option(gloss="number k of candidates (default=1, i.e. perform 1-best viterbi)") public int kBest = 1;
  @Option(gloss="ngram model input file") public String ngramModelFile;
  @Option(gloss="n-gram size (default=3)") public int ngramSize = 3;
  @Option(gloss="n-gram wrapper (default=srilm)") public NgramWrapper ngramWrapper = NgramWrapper.srilm;
  @Option(gloss="allow none-event (default=false)") public boolean allowNoneEvent = false;
  @Option(gloss="allow consecutive events (default=true)") public boolean allowConsecutiveEvents = false;
  @Option public ReorderType reorderType = ReorderType.eventType;

  @Option(gloss="allow bigrams on word level (default=false)") public boolean lmIntegration = false;
  @Option(gloss="use POS tagging at surface level (changes vocabulary) (default=false)") public boolean posAtSurfaceLevel = false;
  @Option(gloss="use gold standard events only as input (default=false)") public boolean useGoldStandardOnly = false;

  @Option(gloss="set n for modified BLEU score") public int modifiedBleuScoreSize = 4;

  @Option public boolean debug = false;

  // Generic
  @Option public int trainStart = 0;
  @Option public int trainEnd = Integer.MAX_VALUE;
  @Option public int testStart = 0;
  @Option public int testEnd = 0;

  // Learning
  @Option public InitType initType = InitType.random;
  @Option public double initSmoothing = 0.01;
  @Option public double initNoise = 1e-3;
  @Option public Random initRandom = new Random(1);
  @Option(gloss="Randomness for permuting data points in online") public Random onlinePermRandom = new Random(1);
  @Option public boolean onlinePerm = false;
  @Option(gloss="If batch size is larger this threshold, store counts rather than infer states") public int batchSizeNewCounts = 1000;

  @OptionSet(name="stage1") public LearnOptions stage1 = new LearnOptions();
  @OptionSet(name="stage2") public LearnOptions stage2 = new LearnOptions();
  @Option(gloss="Parameters object file") public String stagedParamsFile = "";
  @Option(gloss="Output every this number of iterations") public int outputIterFreq = 1;
  @Option(gloss="Output full predictions (for debugging)") public boolean outputFullPred = false;
  @Option(gloss="Output training objective and test predictions on current parameters") public boolean outputCurrentState = false;
  @Option(gloss="Output every this number of examples (for online)") public double outputExampleFreq = 100;
  @Option public int outputNumParamsPerVec = Integer.MAX_VALUE;
  @Option(gloss="This computation involves lots of logs and can be slow") public boolean computeELogZEntropy = false;

  // Generate artificial data
  @Option public int genNumExamples = 0;
  @Option(gloss="Maximum number of tokens per example") public int genMaxTokens = 100;
  @Option public Random genRandom = new Random(1);
  @Option public Random genInitRandom = new Random(1);
  @Option public InitType genInitType = InitType.supervised;

  @Option public int artNumWords = 100;
  @Option public double artAlpha = 0.5;

  // General
  @Option(gloss="Number of parameters per state to print") public int numOutputParams = 10;
  @Option(gloss="Number of threads to use") public int numThreads = Runtime.getRuntime().availableProcessors();
}