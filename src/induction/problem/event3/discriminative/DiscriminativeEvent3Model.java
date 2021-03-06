package induction.problem.event3.discriminative;

import edu.uci.ics.jung.graph.Graph;
import fig.basic.FullStatFig;
import fig.basic.IOUtils;
import induction.LearnOptions.LearningScheme;
import induction.problem.AInferState;
import induction.problem.event3.params.Params;
import fig.basic.Indexer;
import fig.basic.LogInfo;
import fig.exec.Execution;
import fig.record.Record;
import induction.LearnOptions;
import induction.MyCallable;
import induction.Options;
import induction.Options.NgramWrapper;
import induction.Utils;
import induction.ngrams.KylmNgramWrapper;
import induction.ngrams.NgramModel;
import induction.ngrams.RoarkNgramWrapper;
import induction.ngrams.SrilmNgramWrapper;
import induction.problem.AExample;
import induction.problem.AParams;
import induction.problem.AParams.ParamsType;
import induction.problem.APerformance;
import induction.problem.InferSpec;
import induction.problem.Vec;
import induction.problem.VecFactory;
import induction.problem.event3.Event3Model;
import induction.problem.event3.EventType;
import induction.problem.event3.Example;
import induction.problem.event3.Field;
import induction.problem.event3.Widget;
import induction.problem.event3.discriminative.optimizer.DefaultPerceptron;
import induction.problem.event3.discriminative.optimizer.GradientBasedOptimizer;
import induction.problem.event3.discriminative.params.DiscriminativeParams;
import induction.problem.event3.generative.generation.GenWidget;
import induction.problem.event3.generative.generation.GenerationPerformance;
import induction.problem.event3.json.JsonResult;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;


/**
 * A discriminative model of events and their text summaries
 *
 * @author konstas
 */
public class DiscriminativeEvent3Model extends Event3Model implements Serializable
{  
    /**
     * Parameters of the baseline model
     */
    Params baselineModelParams;
    /**
     * maps that contain the total feature counts extracted from the Viterbi search
     * of the oracle model and the model under train
     */
    HashMap<Feature, Double> oracleFeatures, modelFeatures;
        
    Map<List<Integer>, Integer> wordBigramMap, wordNgramMap, wordNegativeNgramMap;
    Map<List<Integer>, Integer>[] fieldNgramsMapPerEventTypeArray;
    /**
     * Keeps count of the number of examples processed so far. Necessary for batch updates
     */
    int numProcessedExamples = 0;
    /**
     * perform k-best Viterbi, which entails using non-local features
     */
    boolean useKBest;
    /**
     * The original size of the vocabulary, defined by the wordIndexer stored in the
     * serialised object of the generative model. This number stays invariable.
     */
    int vocabularySize;    
    
    public DiscriminativeEvent3Model(Options opts)
    {
        super(opts);        
        oracleFeatures = new HashMap<Feature, Double>();
        modelFeatures = new HashMap<Feature, Double>();      
        useKBest = opts.kBest > 1;
        wordNegativeNgramMap = new HashMap();
    }

    @Override
    public void stagedInitParams()
    {
        // Load generative model parameters
        baselineModelParams = loadGenerativeModelParams();
        // Load dependencies model
        if(opts.useDependencies)
            loadDMVModel();
        
        Utils.begin_track("stagedInitParams");
        try {            
        ObjectInputStream ois = IOUtils.openObjIn(opts.stagedParamsFile);
        try
        {
            Utils.log("Loading " + opts.stagedParamsFile);            
            params = newParams();
            params.setVecs((Map<String, Vec>) ois.readObject());            
            if(opts.includeFieldNgramsPerEventTypeFeature)
                fieldNgramsMapPerEventTypeArray = (Map<List<Integer>, Integer>[]) ois.readObject();
            if(opts.includeNegativeNgramsFeature)
                wordNegativeNgramMap = (Map<List<Integer>, Integer>) ois.readObject();            
//            ois.close();
        }
        catch(EOFException eof)
        {
            Utils.log("Suppressing loading error - no wordNegativeNgramMap/fieldNgramsMapPerEventTypeArray object available");
        }
        catch(Exception ioe)
        {
            Utils.log("Error loading "+ opts.stagedParamsFile);
            ioe.printStackTrace(LogInfo.stderr);
//            ioe.printStackTrace();
            Execution.finish();
        }
        finally
        {
            ois.close();
        }
        }
        catch(IOException ioe)
        {
            Utils.log("Error opening "+ opts.stagedParamsFile);
            ioe.printStackTrace(LogInfo.stderr);
//            ioe.printStackTrace();
            Execution.finish();
        }
        LogInfo.end_track();
        loadLengthPredictionModel();
        loadPosTagger();
        loadLanguageModel();
    }
    
    private Params loadGenerativeModelParams()
    {
        Params generativeParams = null;
        Utils.begin_track("generativeModelInitParams");
        try
        {
            Utils.log("Loading " + opts.generativeModelParamsFile);
            ObjectInputStream ois = IOUtils.openObjIn(opts.generativeModelParamsFile);
            wordIndexer = ((Indexer<String>) ois.readObject());
            vocabularySize = W();
            if(useKBest)
            {
                // build a list of all the ngrams            
                populateNgramMaps();
            }
            labelIndexer = ((Indexer<String>) ois.readObject());
            eventTypes = (EventType[]) ois.readObject();
            if(opts.includeFieldNgramsPerEventTypeFeature)
            {
                fieldNgramsMapPerEventTypeArray = new HashMap[eventTypes.length];
                for(int i = 0; i < eventTypes.length; i++)
                    fieldNgramsMapPerEventTypeArray[i] = new HashMap();
            }
            eventTypesBuffer = new ArrayList<EventType>(Arrays.asList(eventTypes));
            // fill in eventTypesNameIndexer
            fieldsMap = new HashMap<Integer, HashMap<String, Integer>>(eventTypes.length);
            for(EventType e: eventTypes)
            {
                eventTypeNameIndexer.add(e.getName());
                HashMap<String, Integer> fields = new HashMap<String, Integer>();
                int i = 0;
                for(Field f : e.getFields())
                {
                    fields.put(f.getName(), i++);
                }
                fields.put("none_f", i++);
                fieldsMap.put(e.getEventTypeIndex(), fields);
            }
            generativeParams = new Params(this, opts, VecFactory.Type.DENSE);
            generativeParams.setVecs((Map<String, Vec>) ois.readObject());
            ois.close();
        }
        catch(Exception ioe)
        {
            Utils.log("Error loading "+ opts.stagedParamsFile);
            ioe.printStackTrace(LogInfo.stderr);
//            ioe.printStackTrace();
            Execution.finish();
        }
        LogInfo.end_track();
        return generativeParams;
    }

    /**
     * Populate the sets of bigrams and trigrams using the observations given from
     * an external .arpa style ngram file. Instead of storing actual String ngrams
     * use the internal representation of integers, from the <code>wordIndexer</code>
     * object.
     */
    private void populateNgramMaps()
    {
//        wordNegativeNgramMap = NgramModel.readNgramsFromArpaFile(opts.ngramModelFile, 2, wordIndexer, false);
        wordNgramMap = NgramModel.readNgramsFromArpaFile(opts.ngramModelFile, 3, wordIndexer, false);
        wordBigramMap = NgramModel.readNgramsFromArpaFile(opts.ngramModelFile, 2, wordIndexer, false);
    }

    public String[] getWordNgramLabels(Map<List<Integer>, Integer> ngrams, int N)
    {
//        Map<List<Integer>, Integer> ngrams = wordNgramMap;
        String labels[] = new String[ngrams.size()];
        for(Entry<List<Integer>, Integer> entry : ngrams.entrySet())
        {
            StringBuilder str = new StringBuilder();
            for(Integer index : entry.getKey())
            {
                str.append(wordToString(index)).append(" ");
            }
            labels[entry.getValue()] = str.toString().trim();
        }
        return labels;
    }
    
    public String[] getFieldNgramLabels(EventType eventType, Map<List<Integer>, Integer> ngrams, int N)
    {
        String labels[] = new String[ngrams.size()];
        for(Entry<List<Integer>, Integer> entry : ngrams.entrySet())
        {
            StringBuilder str = new StringBuilder();
            for(Integer index : entry.getKey())
            {
                str.append(eventType.fieldToString(index)).append(" ");
            }
            labels[entry.getValue()] = str.toString().trim();
        }
        return labels;
    }

    public boolean isUseKBest()
    {
        return useKBest;
    }
    
    public Map<List<Integer>, Integer> getWordNegativeNgramMap()
    {
        return wordNegativeNgramMap;
    }    
    
    public Map<List<Integer>, Integer> getWordNgramMap()
    {
        return wordNgramMap;
    }

    public Map<List<Integer>, Integer> getWordBigramMap()
    {
        return wordBigramMap;
    }

    public Map<List<Integer>, Integer>[] getFieldNgramsMapPerEventTypeArray()
    {
        return fieldNgramsMapPerEventTypeArray;
    }    
    
    @Override
    protected void saveParams(String name)
    {
        try
        {
            ObjectOutputStream oos = IOUtils.openObjOut(Execution.getFile(name + 
                    ".discriminative.params.obj.gz"));
            oos.writeObject(params.getVecs());
            if(opts.includeFieldNgramsPerEventTypeFeature)
                oos.writeObject(fieldNgramsMapPerEventTypeArray);
            if(opts.includeNegativeNgramsFeature);
            oos.writeObject(wordNegativeNgramMap);
            oos.close();
        }
        catch (IOException ex)
        {
            Utils.log(ex.getMessage());
            ex.printStackTrace(LogInfo.stderr);
//            ex.printStackTrace();
        }
    }

    @Override
    protected void supervisedInitParams()
    {
        // Load generative model parameters
        baselineModelParams = loadGenerativeModelParams();
        // Initialise dependencies model
        if(opts.useDependencies)
            initDepsModel();
        params = newParams();
        //do nothing, initialise to zero by default
    }

    protected void initDepsModel()
    {
        // initialize the dependency model's wordIndexer either with lexical items or POS tags
        Indexer<String> depsModelWordIndexer = depsModel.getWordIndexer();
        if(!opts.useTagsAsWords)
            depsModelWordIndexer.addAll(wordIndexer);
        else
        {
            for(String word : wordIndexer.getObjects())
            {
                String tag = Utils.stripWord(word, true, opts.tagDelimiter);
                if(tag != null)
                    depsModelWordIndexer.add(tag);
            } // for
        } // else        
        depsModel.preInit();
        depsModel.supervisedInitParams(VecFactory.Type.SPARSE);
    }
    
    @Override
    protected AParams newParams()
    {
        return new DiscriminativeParams(this, opts, VecFactory.Type.SPARSE, opts.maxNumOfWordsPerField);
    }

    @Override
    protected APerformance newPerformance()
    {
        switch(opts.modelType)
        {
            case discriminativeTrain: return new DiscriminativePerformance(this);
            default: case generate : case generatePcfg : return new GenerationPerformance(this);
        }        
    }

    /**
     * Averaged Perceptron training (Collins 2002)
     * @param name
     * @param lopts 
     */
    @Override
    public void learn(String name, LearnOptions lopts)
    {
        opts.alignmentModel = lopts.alignmentModel; // HACK
        Utils.begin_track("Loading Language Model...");
        if(opts.ngramWrapper == NgramWrapper.kylm)
            ngramModel = new KylmNgramWrapper(opts.ngramModelFile);
        else if(opts.ngramWrapper == NgramWrapper.srilm)
            ngramModel = new SrilmNgramWrapper(opts.ngramModelFile, opts.ngramSize);
        else if(opts.ngramWrapper == NgramWrapper.roark)
            ngramModel = new RoarkNgramWrapper(opts.ngramModelFile);
        LogInfo.end_track();
        Record.begin(name);
        Utils.begin_track("Train: " + name);
        boolean existsTrain = false;
        for(int i = 0; i < examples.size(); i++)
        {
            if(isTrain(i))
            {
                existsTrain = true; break;
            }
        }        
        
        // initialise model
        HashMap<Feature, Double> perceptronSumModel = new HashMap();
        HashMap<Feature, Double[]> perceptronAverageModel = new HashMap();
        
        int batchSize;
        boolean cooling = false;
        switch(lopts.learningScheme)
        {            
            case batch : batchSize = examples.size(); break;    
            case stepwise : batchSize = lopts.miniBatchSize; cooling = true; break;    
            default: case incremental: batchSize = 1;
        }
        // percy's cooling
        GradientBasedOptimizer optimizer = new DefaultPerceptron(
                perceptronSumModel, perceptronAverageModel, 
                examples.size(), 
                batchSize,
                lopts.convergePass, 
                lopts.stepSizeReductionPower,
                lopts.initTemperature);
        //zli's cooling (set initTemperature to 0.1)
//        GradientBasedOptimizer optimizer = new DefaultPerceptron(
//                perceptronSumModel, perceptronAverageModel, 
//                examples.size(), 
//                batchSize,
//                lopts.convergePass, 
//                lopts.initTemperature);
        // we need the cooling scheduling in case we do stepwise updating
        if(!cooling)
            optimizer.setNoCooling();
        Feature baseFeature = new Feature(((DiscriminativeParams)params).baselineWeight, 0);
        Feature hasConsecutiveBigramsFeature = new Feature(((DiscriminativeParams)params).hasConsecutiveBigramsWeight, 0);
        Feature hasConsecutiveTrigramsFeature = new Feature(((DiscriminativeParams)params).hasConsecutiveTrigramsWeight, 0);
        Feature hasConsecutiveWordsFeature = new Feature(((DiscriminativeParams)params).hasConsecutiveWordsWeight, 0);
        for(int iter = 0; iter < lopts.numIters; iter++) // for t = 1...T do
        {
            FullStatFig complexity = new FullStatFig(); // Complexity inference
            Utils.begin_track("Iteration %s/%s: ", Utils.fmt(iter+1), 
                    Utils.fmt(lopts.numIters));
            Record.begin("iteration", iter+1);
            trainPerformance = existsTrain ? newPerformance() : null;
            
            for(int i = 0; i < examples.size(); i++) // for i = 1...N do
            {                
//                Collection<ExampleProcessor> list = new ArrayList(2);
                // perform reranking on the hypergraph. During the recursive call
                // in order to extract D_1 (top derivation) update the modelFeatures
                // map, i.e. compute f(y^). We will need this for the perceptron updates
//                list.add(new ExampleProcessor(
//                        examples.get(i), i, modelFeatures, false, lopts, iter, complexity));
//                // compute oracle and update the oracleFeatures map, i.e. compute f(y+)
//                list.add(new ExampleProcessor(
//                        examples.get(i), i, oracleFeatures, true, lopts, iter, complexity));
//                Utils.parallelForeach(opts.numThreads, list);
//                list.clear();
//                System.out.println(examples.get(i).getName());
                try{
                    ExampleProcessor model = new ExampleProcessor(
                            examples.get(i), i, modelFeatures, false, lopts, iter, complexity);

                    model.call();
                    model = null;
                    ExampleProcessor oracle = new ExampleProcessor(
                            examples.get(i), i, oracleFeatures, true, lopts, iter, complexity);
                    oracle.call();
                    oracle = null;                    
                    
//                    System.out.print("oracle: " + oracleFeatures.get(baseFeature) +
//                                       " - model: " + modelFeatures.get(baseFeature) + 
//                                       " - base sum: " + baseFeature.getValue()
//                                       );
//                    if(perceptronSumModel.containsKey(hasConsecutiveBigramsFeature))
//                        System.out.println(" oracle: " + oracleFeatures.get(hasConsecutiveBigramsFeature) +
//                                           " - model: " + modelFeatures.get(hasConsecutiveBigramsFeature) + 
//                                           " - consBigrams sum: " + hasConsecutiveBigramsFeature.getValue()
//                                           );
//                    if(perceptronSumModel.containsKey(hasConsecutiveTrigramsFeature))
//                        System.out.println(" oracle: " + oracleFeatures.get(hasConsecutiveTrigramsFeature) +
//                                           " - model: " + modelFeatures.get(hasConsecutiveTrigramsFeature) + 
//                                           " - consTrigrams sum: " + hasConsecutiveTrigramsFeature.getValue()
//                                           );
//                    else if(perceptronSumModel.containsKey(hasConsecutiveWordsFeature))
//                        System.out.println(" oracle: " + oracleFeatures.get(hasConsecutiveWordsFeature) +
//                                           " - model: " + modelFeatures.get(hasConsecutiveWordsFeature) + 
//                                           " - hasCons sum: " + hasConsecutiveWordsFeature.getValue()
//                                           );
//                        
//                    else
//                        System.out.println();
                }                
                catch(Exception e){
                    e.printStackTrace();
                    LogInfo.error(e);
                }
                
                numProcessedExamples++;
                // update perceptron if necessary (batch update)
                updateOptimizer(false, optimizer);
            } // for (all examples)
            // purge any unprocessed examples
            if(lopts.learningScheme == LearningScheme.stepwise)
                updateOptimizer(true, optimizer);
            // update the internal average model
            ((DefaultPerceptron)optimizer).forceUpdateAverageModel();
            record(String.valueOf(iter), name, complexity, true);            
            LogInfo.end_track();
            Record.end();
            // Final
            if (iter == lopts.numIters - 1)
            {
                LogInfo.track("Final", true);
                if(trainPerformance != null)
                    trainPerformance.record("train");
                LogInfo.end_track();
            }
            if (Execution.shouldBail())
                lopts.numIters = iter;
        } // for (all iterations)       
        // use average model weights instead of sum 
        // (reduces overfitting according to Collins, 2002)
        ((DefaultPerceptron)optimizer).updateParamsWithAvgWeights();                
        
//        System.out.println(((DiscriminativeParams)params).outputDiscriminativeOnly(ParamsType.COUNTS));
        if(!opts.dontOutputParams)
        {
            saveParams(name);            
//            params.outputNonZero(Execution.getFile(name+".nonEmpty.params"), ParamsType.COUNTS);
            LogInfo.logs(((DiscriminativeParams)params).outputDiscriminativeOnly(ParamsType.COUNTS));
        }        
        LogInfo.end_track();
        Record.end();
    }
    
    /**
     * Iterative Parameter mixing using averaged Perceptron training (McDonald 2010)
     * @param name
     * @param lopts 
     */    
    public void learnParallel(String name, LearnOptions lopts)
    {
        opts.alignmentModel = lopts.alignmentModel; // HACK
        Utils.begin_track("Loading Language Model...");
        if(opts.ngramWrapper == NgramWrapper.kylm)
            ngramModel = new KylmNgramWrapper(opts.ngramModelFile);
        else if(opts.ngramWrapper == NgramWrapper.srilm)
            ngramModel = new SrilmNgramWrapper(opts.ngramModelFile, opts.ngramSize);
        else if(opts.ngramWrapper == NgramWrapper.roark)
            ngramModel = new RoarkNgramWrapper(opts.ngramModelFile);
        LogInfo.end_track();
        Record.begin(name);
        Utils.begin_track("Train: " + name);
        boolean existsTrain = false;
        for(int i = 0; i < examples.size(); i++)
        {
            if(isTrain(i))
            {
                existsTrain = true; break;
            }
        }        
        
        // initialise model
        HashMap<Feature, Double> perceptronSumModel = new HashMap();
        HashMap<Feature, Double[]> perceptronAverageModel = new HashMap();
        
        int batchSize;
        boolean cooling = false;
        switch(lopts.learningScheme)
        {            
            case batch : batchSize = examples.size(); break;    
            case stepwise : batchSize = lopts.miniBatchSize; cooling = true; break;    
            default: case incremental: batchSize = 1;
        }
        // percy's cooling
        GradientBasedOptimizer optimizer = new DefaultPerceptron(
                perceptronSumModel, perceptronAverageModel, 
                examples.size(), 
                batchSize,
                lopts.convergePass, 
                lopts.stepSizeReductionPower,
                lopts.initTemperature);
        //zli's cooling (set initTemperature to 0.1)
//        GradientBasedOptimizer optimizer = new DefaultPerceptron(
//                perceptronSumModel, perceptronAverageModel, 
//                examples.size(), 
//                batchSize,
//                lopts.convergePass, 
//                lopts.initTemperature);
        // we need the cooling scheduling in case we do stepwise updating
        if(!cooling)
            optimizer.setNoCooling();
        Feature baseFeature = new Feature(((DiscriminativeParams)params).baselineWeight, 0);       
        Feature hasEmptyValueFeature = new Feature(((DiscriminativeParams)params).hasEmptyValueWeight, 0);
        Feature hasConsecutiveWordsFeature = new Feature(((DiscriminativeParams)params).hasConsecutiveWordsWeight, 0);
        Feature hasConsecutiveBigramsFeature = new Feature(((DiscriminativeParams)params).hasConsecutiveBigramsWeight, 0);
        Feature hasConsecutiveTrigramsFeature = new Feature(((DiscriminativeParams)params).hasConsecutiveTrigramsWeight, 0);
        for(int iter = 0; iter < lopts.numIters; iter++) // for t = 1...T do
        {
            FullStatFig complexity = new FullStatFig(); // Complexity inference
            Utils.begin_track("Iteration %s/%s: ", Utils.fmt(iter+1), 
                    Utils.fmt(lopts.numIters));
            Record.begin("iteration", iter+1);
            trainPerformance = existsTrain ? newPerformance() : null;
            
            for(int i = 0; i < examples.size(); i++) // for i = 1...N do
            {                
//                Collection<ExampleProcessor> list = new ArrayList(2);
                // perform reranking on the hypergraph. During the recursive call
                // in order to extract D_1 (top derivation) update the modelFeatures
                // map, i.e. compute f(y^). We will need this for the perceptron updates
//                list.add(new ExampleProcessor(
//                        examples.get(i), i, modelFeatures, false, lopts, iter, complexity));
//                // compute oracle and update the oracleFeatures map, i.e. compute f(y+)
//                list.add(new ExampleProcessor(
//                        examples.get(i), i, oracleFeatures, true, lopts, iter, complexity));
//                Utils.parallelForeach(opts.numThreads, list);
//                list.clear();
//                System.out.println(examples.get(i).getName());
                try{
                    ExampleProcessor model = new ExampleProcessor(
                            examples.get(i), i, modelFeatures, false, lopts, iter, complexity);

                    model.call();
                    model = null;
                    ExampleProcessor oracle = new ExampleProcessor(
                            examples.get(i), i, oracleFeatures, true, lopts, iter, complexity);
                    oracle.call();
                    oracle = null;                    
                    
//                    System.out.print("oracle: " + oracleFeatures.get(baseFeature) +
//                                       " - model: " + modelFeatures.get(baseFeature) + 
//                                       " - base sum: " + baseFeature.getValue()
//                                       );
//                    if(perceptronSumModel.containsKey(hasConsecutiveBigramsFeature))
//                        System.out.println(" oracle: " + oracleFeatures.get(hasConsecutiveBigramsFeature) +
//                                           " - model: " + modelFeatures.get(hasConsecutiveBigramsFeature) + 
//                                           " - consBigrams sum: " + hasConsecutiveBigramsFeature.getValue()
//                                           );
//                    if(perceptronSumModel.containsKey(hasConsecutiveTrigramsFeature))
//                        System.out.println(" oracle: " + oracleFeatures.get(hasConsecutiveTrigramsFeature) +
//                                           " - model: " + modelFeatures.get(hasConsecutiveTrigramsFeature) + 
//                                           " - consTrigrams sum: " + hasConsecutiveTrigramsFeature.getValue()
//                                           );
//                    else if(perceptronSumModel.containsKey(hasConsecutiveWordsFeature))
//                        System.out.println(" oracle: " + oracleFeatures.get(hasConsecutiveWordsFeature) +
//                                           " - model: " + modelFeatures.get(hasConsecutiveWordsFeature) + 
//                                           " - hasCons sum: " + hasConsecutiveWordsFeature.getValue()
//                                           );
//                        
//                    else
//                        System.out.println();
                }                
                catch(Exception e){
                    e.printStackTrace();
                    LogInfo.error(e);
                }
                
                numProcessedExamples++;
                // update perceptron if necessary (batch update)
                updateOptimizer(false, optimizer);
            } // for (all examples)
            // purge any unprocessed examples
            if(lopts.learningScheme == LearningScheme.stepwise)
                updateOptimizer(true, optimizer);
            // update the internal average model
            ((DefaultPerceptron)optimizer).forceUpdateAverageModel();
            record(String.valueOf(iter), name, complexity, true);            
            LogInfo.end_track();
            Record.end();
            // Final
            if (iter == lopts.numIters - 1)
            {
                LogInfo.track("Final", true);
                if(trainPerformance != null)
                    trainPerformance.record("train");
                LogInfo.end_track();
            }
            if (Execution.shouldBail())
                lopts.numIters = iter;
        } // for (all iterations)       
        // use average model weights instead of sum 
        // (reduces overfitting according to Collins, 2002)
        ((DefaultPerceptron)optimizer).updateParamsWithAvgWeights();
        
        System.out.println(((DiscriminativeParams)params).outputDiscriminativeOnly(ParamsType.COUNTS));
        if(!opts.dontOutputParams)
        {
            saveParams(name);
            params.output(Execution.getFile(name+".params"), ParamsType.COUNTS);
        }        
        LogInfo.end_track();
        Record.end();
    }

    public void updateOptimizer(boolean forceUpdate, GradientBasedOptimizer optimizer)
    {
        if(forceUpdate || numProcessedExamples >= optimizer.getBatchSize())
        {            
            optimizer.updateModel(oracleFeatures, modelFeatures);            
//            reset_baseline_feat();            
            oracleFeatures.clear();
            modelFeatures.clear();
            numProcessedExamples = 0;            
            synchronized(trainPerformance)
            {
                ((DiscriminativePerformance)trainPerformance).add(optimizer.getGradientNorm());
            }
        }
    }
    
    public void resetBaselineFeature(GradientBasedOptimizer optimizer)
    {        
//        optimizer.setFeatureWeight(((DiscriminativeParams)params).baselineWeight, 1.0);
    }
    @Override
    public void generate(String name, LearnOptions lopts)
    {
        opts.alignmentModel = lopts.alignmentModel; // HACK 
//        Utils.begin_track("Loading Language Model...");
//        if(opts.ngramWrapper == NgramWrapper.kylm)
//            ngramModel = new KylmNgramWrapper(opts.ngramModelFile);
//        else if(opts.ngramWrapper == NgramWrapper.srilm)
//            ngramModel = new SrilmNgramWrapper(opts.ngramModelFile, opts.ngramSize);
//        else if(opts.ngramWrapper == NgramWrapper.roark)
//            ngramModel = new RoarkNgramWrapper(opts.ngramModelFile);
//        LogInfo.end_track();
        // Complexity inference (number of hypergraph nodes)
        FullStatFig complexity = new FullStatFig();
        testPerformance = newPerformance();       
        try
        {
            testFullPredOut = (opts.outputFullPred) ?
                IOUtils.openOut(Execution.getFile(
                name+".test.full-pred-gen")) : null;
            testPredOut = IOUtils.openOut(Execution.getFile(name+".tst.xml"));
            // write prediction file header, conforming to SGML NIST standard
            testPredOut.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                "<mteval>\n" +
                                "<tstset setid=\"" + name + "\" srclang=\"English\" " +
                                "trglang=\"English\" sysid=\"sample_system\">");
        }
        catch(Exception ioe)
        {
            Utils.begin_track("Error opening file(s) for writing. No output will be written!");
            LogInfo.end_track();
        }                
        Utils.begin_track("Generation-step " + name);        
        Collection<ExampleProcessor> list = new ArrayList(examples.size());
        for(int i = 0; i < examples.size(); i++)
        {
            list.add(new ExampleProcessor(
                    examples.get(i), i, modelFeatures, false, lopts, 0, complexity));
//            try{
//            ExampleProcessor model = new ExampleProcessor(
//                    examples.get(i), modelFeatures, false, lopts, 0, complexity);
//            model.call();
//            model = null;
//            }
//            catch(Exception e){}
        }
        Utils.parallelForeach(opts.numThreads, list);
        LogInfo.end_track();
        list.clear();

        if(testFullPredOut != null) testFullPredOut.close();
        if(testPredOut != null)
        {
            // write prediction file footer, conforming to SGML NIST standard
            testPredOut.println("</tstset>\n</mteval>");
            testPredOut.close();
        }
        Execution.putOutput("currExample", examples.size());

        // Final
//        testPerformance.output(Execution.getFile(name+".test.performance"));
        Record.begin("generation");
        record("results", name, complexity, true);
        Record.end();
        LogInfo.end_track();
        
        if(!opts.dontOutputParams)
        {        
            params.outputNonZero(Execution.getFile(name+".nonEmpty.params"), ParamsType.COUNTS);
        }
    }        
    
    /**
     * helper method for testing the discriminative learning scheme. 
     * Simulates learn(...) method from the DiscriminativeEvent3Model class
     * for a number of examples.
     * @return the average Viterbi log probability
     */
    public double testDiscriminativeLearn(String name, LearnOptions lopts)
    {
        learn(name, lopts);
        return trainPerformance.getAccuracy();
    }

    /**
     * helper method for testing the generation output. Simulates generate(...) method
     * for a single example without the thread mechanism
     * @return a String with the generated SGML text output (contains results as well)
     */
    public String testGenerate(String name, LearnOptions lopts)
    {
        opts.alignmentModel = lopts.alignmentModel;
////        ngramModel = new KylmNgramWrapper(opts.ngramModelFile);
//        ngramModel = new SrilmNgramWrapper(opts.ngramModelFile, opts.ngramSize);
        FullStatFig complexity = new FullStatFig();        
        testPerformance = newPerformance();
        AExample ex = examples.get(0);
        Widget bestWidget = null;
        try{
//            for(int i = 0; i < examples.size(); i++)
//            {
//                ExampleProcessor model = new ExampleProcessor(
//                    examples.get(i), i, modelFeatures, false, lopts, 0, complexity);
//                model.call();
//                bestWidget = model.bestWidget;
//            }                                    
            ExampleProcessor model = new ExampleProcessor(
                ex, 0, modelFeatures, false, lopts, 0, complexity);
            model.call();
            bestWidget = model.bestWidget;
            System.out.println(widgetToFullString(ex, bestWidget));
//            System.out.println(((DiscriminativeParams)params).outputDiscriminativeOnly());
        }
        catch(Exception e){
            e.printStackTrace();
        }        
        
        return widgetToSGMLOutput(ex, bestWidget);
    }
    
    public Params getBaselineModelParams()
    {
        return baselineModelParams;
    }

    public HashMap getOracleFeatures()
    {
        return oracleFeatures;
    }

    public HashMap getModelFeatures()
    {
        return modelFeatures;
    }
    
    public AInferState createInferState(Example ex, double stepSize,
            LearnOptions lopts, int iter, boolean calculateOracle)
    {
        InferSpec ispec = new InferSpec(1, !lopts.hardUpdate, true, lopts.hardUpdate,
                      false, lopts.mixParamsCounts, lopts.useVarUpdates,
                      stepSize, iter);
        if(calculateOracle)
            return new DiscriminativeInferStateOracle(
                    this, ex, (Params)params, null, ispec, ngramModel, useKBest);
        else 
            return new DiscriminativeInferState(
                    this, ex, (Params)params, null, ispec, ngramModel, useKBest);

    }
    
    @Override
    protected AInferState newInferState(AExample aex, AParams aweights, AParams acounts,
                                       InferSpec ispec)
    {        
        Example ex = (Example)aex;
        Params weights = (Params)aweights;
        Params counts = (Params)acounts;        
        return new DiscriminativeInferState(this, ex, weights, counts, ispec, ngramModel, useKBest);
    }

    @Override
    protected AInferState newInferState(AExample aex, AParams aweights, AParams acounts,
                                           InferSpec ispec, Graph graph)
    {     
        Example ex = (Example)aex;
        Params weights = (Params)aweights;
        Params counts = (Params)acounts;
        return new DiscriminativeInferState(this, ex, weights, counts, ispec, ngramModel, useKBest, graph);        
    }

    protected class ExampleProcessor extends MyCallable
    {
        private AExample ex;
        private int iter, i;
        private HashMap<Feature, Double> features;
        private LearnOptions lopts;
        private final FullStatFig complexity;
        private final boolean calculateOracle;
        private Widget bestWidget;
        
        public ExampleProcessor(AExample ex, int i, HashMap<Feature, Double> features,
                                boolean calculateOracle, LearnOptions lopts, 
                                int iter, FullStatFig complexity)
        {            
            this.ex = ex;
            this.i = i;
            this.features = features;
            this.lopts = lopts;
            this.iter = iter;
            this.complexity = complexity;
            this.calculateOracle = calculateOracle;
        }
        @Override
        public Object call() throws Exception
        {
            // create an inference state model
            DiscriminativeInferState inferState = 
                    (DiscriminativeInferState) createInferState(
                    (Example)ex, 1, lopts, iter, calculateOracle);            
            // create hypergraph - precompute local features on the fly
            inferState.setCalculateOracle(calculateOracle);       
            try{
            inferState.createHypergraph(ex.N());
            inferState.setFeatures(features);
            inferState.doInference();
            }
            catch(Exception e)
            {
                System.out.println("Error in example: " + ex.getName());
                e.printStackTrace();
            }
            // update statistics
            synchronized(complexity)
            {
                complexity.add(inferState.getComplexity());
            }
            // process results
            if(opts.modelType == Options.ModelType.discriminativeTrain && !calculateOracle)
                synchronized(trainPerformance)
                {
                    trainPerformance.add(inferState.stats());
                    trainPerformance.add(ex, inferState.bestWidget);
                    if (opts.outputExampleFreq != 0 && i % opts.outputExampleFreq == 0)
                    {
                        Utils.begin_track("Example %s/%s: %s", Utils.fmt(i+1),
                                 Utils.fmt(examples.size()), summary(i));
//                        System.out.println("\n" + GenerationPerformance.widgetToString((GenWidget)inferState.bestWidget));
//                        System.out.println("\n" + widgetToFullString(ex, inferState.bestWidget));
                        Execution.putOutput("currExample", i);
                        LogInfo.end_track();
                    }
//                    System.out.println("\n" + widgetToFullString(ex, inferState.bestWidget));
                }
//            else
//                System.out.println(widgetToFullString(ex, inferState.bestWidget));
            if(opts.modelType == Options.ModelType.generate || 
               opts.modelType == Options.ModelType.generatePcfg)
                synchronized(testPerformance)
                {
                    testPerformance.add(inferState.stats());
                    testPerformance.add(ex, inferState.bestWidget);
                    bestWidget = inferState.bestWidget;
                    if(testPredOut != null)
                    {
                        testPredOut.println(widgetToSGMLOutput(ex, inferState.bestWidget));                        
                    }
                    if(testFullPredOut != null)
                    {
                        testFullPredOut.println(widgetToFullString(ex, inferState.bestWidget));
                    }
                    if (opts.outputExampleFreq != 0 && i % opts.outputExampleFreq == 0)
                    {
                        Utils.begin_track("Example %s/%s: %s", Utils.fmt(i+1),
                                 Utils.fmt(examples.size()), summary(i));
                        LogInfo.logs(GenerationPerformance.widgetToString(wordIndexer, (GenWidget)inferState.bestWidget, opts.tagDelimiter));
                        Execution.putOutput("currExample", i);
                        LogInfo.end_track();
                    }
            }            
            return null;
        }
    }
    
    protected JsonExampleProcessor addExampleJson(int i, JsonResult[] results, AExample ex, 
                AParams counts, double temperature, LearnOptions lopts, int iter, 
                FullStatFig complexity, APerformance performance, Properties dictionary)
    {
        return new JsonDiscriminativeExampleProcessor(i, results, ex, counts, temperature, lopts, iter, complexity, performance, dictionary);
    }
    
    protected class JsonDiscriminativeExampleProcessor extends JsonExampleProcessor 
    {
        public JsonDiscriminativeExampleProcessor(int i, JsonResult[] results, AExample ex, 
                AParams counts, double temperature, LearnOptions lopts, int iter, 
                FullStatFig complexity, APerformance performance, Properties dictionary)
        {
            super(i, results, ex, counts, temperature, lopts, iter, complexity, performance, dictionary);
        }
        
        @Override
        public JsonResult call() throws Exception
        {
            boolean calculateOracle = false;
            // create an inference state model
            DiscriminativeInferState inferState = 
                    (DiscriminativeInferState) createInferState(
                    (Example)ex, 1, lopts, iter, calculateOracle);            
            // create hypergraph - precompute local features on the fly
            inferState.setCalculateOracle(calculateOracle);       
            try{
            inferState.createHypergraph(ex.N());
            inferState.setFeatures(modelFeatures);
            inferState.doInference();
            }
            catch(Exception e)
            {
                System.out.println("Error in example: " + ex.getName());
                e.printStackTrace();
            }            
            
            synchronized(performance)
            {
                performance.add(ex, inferState.bestWidget);
            }
            synchronized(results)
            {
                results[i] = widgetToJson(i, ex, inferState.bestWidget, dictionary);
            }
            return null;
        }
    }
}