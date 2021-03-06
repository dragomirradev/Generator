package induction.problem.dmv.params;

import induction.Options;
import induction.problem.AParams;
import induction.problem.Vec;
import induction.problem.VecFactory;
import induction.problem.dmv.Constants;
import induction.problem.dmv.generative.GenerativeDMVModel;
import java.io.PrintWriter;
import java.util.List;

/**
 *
 * @author konstas
 */
public class DMVParams extends AParams
{
    public int W;
    public Vec starts;
    public Vec[][] continues, deps;
    private final GenerativeDMVModel model;
    private final Options opts;
    private final int[] wordIndexerLengths;
    private final String vocabulary[];
    
    public DMVParams(GenerativeDMVModel model, Options opts, VecFactory.Type vectorType)
    {
        super(model);
        this.model = model;
        this.opts = opts;
        vocabulary = model.wordsToStringArray();
        W = model.W();
        starts = VecFactory.zeros(vectorType, W);
        addVec("S", starts);
        continues = VecFactory.zeros3(vectorType, W, Constants.R, Constants.F);        
        addVec(getLabels(W, Constants.R, "C ", vocabulary, Constants.R_STR), continues);
        wordIndexerLengths = new int[W];
        for(int i = 0; i < wordIndexerLengths.length; i++)
            wordIndexerLengths[i] = model.wordIndexerLength(i);
        deps = VecFactory.zeros3(vectorType, W, Constants.D, wordIndexerLengths);
        addVec(getLabels(W, Constants.D, "D ", vocabulary, Constants.D_STR), deps);
    }
    
    @Override
    public String output(ParamsType paramsType)
    {
        
        StringBuilder out = new StringBuilder();
        if(paramsType == ParamsType.PROBS)
            out.append(forEachProb(starts, getLabels(W, "S ", vocabulary)));
        else
            out.append(forEachCount(starts, getLabels(W, "S ", vocabulary)));
        out.append("\n");
        String[][][] labels = getLabels(W, Constants.R, Constants.F, "C ", vocabulary, 
                Constants.R_STR, Constants.F_STR);
        for(int i = 0; i < continues.length; i++)
        {            
            for(int j = 0; j < continues[0].length; j++)
            {
                Vec v = continues[i][j];
                if(paramsType == ParamsType.PROBS)
                    out.append(forEachProb(v, labels[i][j]));
                else
                    out.append(forEachCount(v, labels[i][j]));
            }
        }        
        out.append("\n");
        List<String>[][] labelsList = getLabels(W, Constants.D, wordIndexerLengths, "D ", vocabulary, 
                Constants.D_STR, model.wordIndexerToArray());
        for(int i = 0; i < deps.length; i++)
        {
            for(int j = 0; j < deps[0].length; j++)
            {
                Vec v = deps[i][j];
                if(paramsType == ParamsType.PROBS)
                    out.append(forEachProb(v, labelsList[i][j].toArray(new String[0])));
                else
                    out.append(forEachCount(v, labelsList[i][j].toArray(new String[0])));
            }
        }
        return out.toString();
    }

    @Override
    public void outputNonZero(ParamsType paramsType, PrintWriter out)
    {
//        StringBuilder out = new StringBuilder();
        if(paramsType == ParamsType.PROBS)
            out.append(forEachProbNonZero(starts, getLabels(W, "S ", vocabulary)));
        else
            out.append(forEachCountNonZero(starts, getLabels(W, "S ", vocabulary)));
        out.append("\n");
        String[][][] labels = getLabels(W, Constants.R, Constants.F, "C ", vocabulary, 
                Constants.R_STR, Constants.F_STR);
        for(int i = 0; i < continues.length; i++)
        {            
            for(int j = 0; j < continues[0].length; j++)
            {
                Vec v = continues[i][j];
                if(paramsType == ParamsType.PROBS)
                    out.append(forEachProbNonZero(v, labels[i][j]));
                else
                    out.append(forEachCountNonZero(v, labels[i][j]));
            }
        }        
        out.append("\n");
        List<String>[][] labelsList = getLabels(W, Constants.D, wordIndexerLengths, "D ", vocabulary, 
                Constants.D_STR, model.wordIndexerToArray());
        for(int i = 0; i < deps.length; i++)
        {
            for(int j = 0; j < deps[0].length; j++)
            {
                Vec v = deps[i][j];
                if(paramsType == ParamsType.PROBS)
                    out.append(forEachProbNonZero(v, labelsList[i][j].toArray(new String[0])));
                else
                    out.append(forEachCountNonZero(v, labelsList[i][j].toArray(new String[0])));
            }
        }
//        return out.toString();
    }
    
}