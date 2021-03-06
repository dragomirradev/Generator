package induction;

import fig.basic.*;

public class LearnOptions {
  // Learning
  @Option(gloss="Number of training iterations") public int numIters = 0;  
  @Option(gloss="Online") public boolean online = false;
  @Option(gloss="Incremental EM (keep around suff stats)") public boolean incremental = false;
  @Option(gloss="Viterbi/Hard EM") public boolean hardUpdate = false;
  @Option public double stepSizeOffset = 2;
  @Option(gloss="Step size power 1/T^power") public double stepSizeReductionPower = 0.5;
  @Option(gloss="Add sufficient statistics to parameters (!)") public boolean mixParamsCounts = false;
  @Option(gloss="Regular stepwise EM") public boolean convexCombUpdate = false;
  @Option(gloss="Initial temperature") public double initTemperature = 1;
  @Option(gloss="Final temperature") public double finalTemperature = 1;
  @Option(gloss="Add smoothing when compute MAP") public double smoothing = 0;
  @Option(gloss="Use variational updates") public boolean useVarUpdates = false;
  @Option(gloss="CFG root rule weight threshold") public double cfgThreshold = 0;
  
  @Option public Options.AlignmentModel alignmentModel = Options.AlignmentModel.m1;

  @Option public boolean miniBatches = false;
  @Option public int miniBatchSize = 30;
  @Option public int convergePass = 1;  
  public enum LearningScheme { incremental, stepwise, batch };
  @Option public LearningScheme learningScheme = LearningScheme.incremental;
}
