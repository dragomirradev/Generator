package induction.problem.event3;

import fig.basic.Indexer;
import induction.problem.event3.params.EventTypeParams;
import induction.problem.event3.params.SymFieldParams;
import induction.problem.event3.params.StrFieldParams;
import induction.problem.event3.params.NumFieldParams;
import induction.problem.event3.params.CatFieldParams;
import induction.problem.event3.params.Params;
import induction.problem.event3.params.Parameters;
import induction.Utils;
import induction.problem.AHypergraphInferState;
import induction.problem.AModel;
import induction.problem.AParams;
import induction.problem.InferSpec;
import fig.basic.Pair;
import induction.DepHead;
import induction.problem.dmv.params.DMVParams;
import induction.problem.event3.params.FieldParams;
import java.util.Arrays;

/**
 *
 * @author konstas
 */
public abstract class Event3InferState
                                        extends AHypergraphInferState//<Example>
        <Widget, Example, Params>
{
    protected double[] segPenalty;
    protected int[] words, nums;
    protected int[] labels;
    protected int L, N, wildcard_pc;

    public Event3InferState(Event3Model model, Example ex, Params params,
            Params counts, InferSpec ispec)
    {
        super(model, ex, params, counts, ispec);

    }

    @Override
    protected void initInferState(AModel model, int textLength)
    {
        wildcard_pc = -1;
        L = opts.maxPhraseLength;
        segPenalty = new double[L + 1];
        for(int l = 0; l < L +1; l++)
        {
            segPenalty[l] = Math.exp(-Math.pow(l, opts.segPenalty));
        }
        N = textLength; //ex.N();
    }
    
    protected int[] newMatrixOne()
    {
        int[] out = new int[N];        
        Arrays.fill(out, -1);
        return out;
    }
    
    protected int[][] newMatrix()
    {        
//        int[][] out = new int[((Event3Model)model).C][ex.N()];
        int[][] out = new int[((Event3Model)model).C][N];
        for(int i = 0; i < out.length; i++)
        {
            Arrays.fill(out[i], -1);
        }
        return out;
    }

    protected int end(int i, int N)
    {
        return Math.min(i + L, N);
    }

    // FUTURE: speed up these lookups
    protected Field getField(int event, int field)
    {
        return ex.events.get(event).getFields()[field];
    }
    protected int getValue(int event, int field)
    {
        return ex.events.get(event).getValues().get(field);
    }
    protected EventTypeParams getEventTypeParams(int event)
    {
        return params.eventTypeParams[ex.events.get(event).getEventTypeIndex()];
    }
    protected EventTypeParams getEventTypeCounts(int event)
    {
        return counts.eventTypeParams[ex.events.get(event).getEventTypeIndex()];
    }
    protected FieldParams getFieldParams(int event, int field)
    {
        FieldParams fp = null;        
        fp = (FieldParams)getEventTypeParams(event).fieldParams[field];        
        return fp;
    }
    protected FieldParams getFieldCounts(int event, int field)
    {
        return (FieldParams)getEventTypeCounts(event).fieldParams[field];
    }
    protected NumFieldParams getNumFieldParams(int event, int field)
    {
        AParams p = getEventTypeParams(event).fieldParams[field];
        if(p instanceof NumFieldParams) return (NumFieldParams)p;
        throw Utils.impossible();
    }
    protected NumFieldParams getNumFieldCounts(int event, int field)
    {
        AParams p = getEventTypeCounts(event).fieldParams[field];
        if(p instanceof NumFieldParams) return (NumFieldParams)p;
        throw Utils.impossible();
    }
    protected CatFieldParams getCatFieldParams(int event, int field)
    {
        AParams p = getEventTypeParams(event).fieldParams[field];
        if(p instanceof CatFieldParams) return (CatFieldParams)p;
        throw Utils.impossible();
    }
    protected CatFieldParams getCatFieldCounts(int event, int field)
    {
        AParams p = getEventTypeCounts(event).fieldParams[field];
        if(p instanceof CatFieldParams) return (CatFieldParams)p;
        throw Utils.impossible();
    }
    protected SymFieldParams getSymFieldParams(int event, int field)
    {
        AParams p = getEventTypeParams(event).fieldParams[field];
        if(p instanceof SymFieldParams) return (SymFieldParams)p;
        throw Utils.impossible();
    }
    protected SymFieldParams getSymFieldCounts(int event, int field)
    {
        AParams p = getEventTypeCounts(event).fieldParams[field];
        if(p instanceof SymFieldParams) return (SymFieldParams)p;
        throw Utils.impossible();
    }
    protected StrFieldParams getStrFieldParams(int event, int field)
    {
        AParams p = getEventTypeParams(event).fieldParams[field];
        if(p instanceof StrFieldParams) return (StrFieldParams)p;
        throw Utils.impossible();
    }
    protected StrFieldParams getStrFieldCounts(int event, int field)
    {
        AParams p = getEventTypeCounts(event).fieldParams[field];
        if(p instanceof StrFieldParams) return (StrFieldParams)p;
        throw Utils.impossible();
    }
    protected DMVParams getDepsParams()
    {
        return ((Event3Model)model).getDepsModel().getParams();
    }
    protected induction.problem.Pair<DepHead> getLeafDepHead(int word, int pos)
    {
        int indexInDepModel = getIndexOfWordInDepModel(word);
        DepHead head = null;
        double weight = Double.MIN_VALUE;
        if(indexInDepModel > -1)
        {
            weight = get(getDepsParams().starts, indexInDepModel);
            head = new DepHead(indexInDepModel, pos, weight);
        }        
        return new induction.problem.Pair<DepHead>(weight, head);
    }
    
    protected induction.problem.Pair<DepHead> getLeafCDDepHead(int pos)
    {
        int index = ((Event3Model)model).getDepsModel().getWordIndexer().getIndex("CD");
        double weight = get(getDepsParams().starts, index);
        DepHead head = new DepHead(index, pos, weight);
        
        return new induction.problem.Pair<DepHead>(weight, head);
    }
    protected int getIndexOfWordInDepModel(int wordIn)
    {
        Integer w = ((Event3Model)model).getDepsCrossWordMap().get(wordIn);
        return w == null ? -1 : w;
    }
    private boolean iterInRange(Pair<Integer, Integer> interval)
    {
        return ispec.iter >= interval.getFirst().intValue() &&
                (interval.getSecond().intValue() == -1 ||
                ispec.iter < interval.getSecond().intValue());
    }
    private boolean prevIterInRange(Pair<Integer, Integer> interval)
    {
        return ispec.iter - 1 >= interval.getFirst().intValue() &&
                (interval.getSecond().intValue() == -1 ||
                ispec.iter - 1 < interval.getSecond().intValue());
    }
    // Options which change depending on iteration
    protected boolean indepEventTypes()
    {
        return iterInRange(opts.indepEventTypes);
    }
    protected boolean indepFields()
    {
        return iterInRange(opts.indepFields);
    }
    protected boolean indepWords()
    {
        return iterInRange(opts.indepWords);
    }
    protected boolean newEventTypeFieldPerWord()
    {
        return iterInRange(opts.newEventTypeFieldPerWord);
    }
    protected boolean newFieldPerWord()
    {
        return iterInRange(opts.newFieldPerWord);
    }
    protected boolean oneEventPerExample()
    {
        return iterInRange(opts.oneEventPerExample);
    }
    protected boolean oneFieldPerEvent()
    {
        return iterInRange(opts.oneFieldPerEvent);
    }
    protected boolean genLabels()
    {
        return iterInRange(opts.genLabels);
    }

    protected boolean useFieldSets(int eventTypeIndex)
    {
        return ((Event3Model)model).getEventTypes()[eventTypeIndex].useFieldSets &&
                iterInRange(opts.useFieldSets);
    }

    // For smooth q-handoff
    protected boolean prevIndepEventTypes()
    {
        return prevIterInRange(opts.indepEventTypes);
    }
    protected boolean prevIndepFields()
    {
        return prevIterInRange(opts.indepFields);
    }
    protected boolean prevIndepWords()
    {
        return prevIterInRange(opts.indepWords);
    }
    protected boolean prevGenLabels()
    {
        return prevIterInRange(opts.genLabels);
    }

    // Rounding schemes for generating numbers
    protected int roundUp(int x)
    {
        return  (x + Parameters.ROUND_SPACING-1) / Parameters.ROUND_SPACING *
                Parameters.ROUND_SPACING;
    }
    protected int roundDown(int x)
    {
        return  x / Parameters.ROUND_SPACING * Parameters.ROUND_SPACING;
    }
    protected int roundClose(int x)
    {
        return  (x + Parameters.ROUND_SPACING/2) / Parameters.ROUND_SPACING *
                Parameters.ROUND_SPACING;
    }

    protected boolean allowNone(int c, int pc)
    {
        return pc == wildcard_pc || !Constants.setContains(pc, c);
    }

    protected boolean allowReal(int c, int pc)
    {
        return pc == wildcard_pc || Constants.setContains(pc, c);
    }

    protected double getEventTypeGivenWord(int t, int w)
    {
        if (opts.includeEventTypeGivenWord)
            return get(params.eventTypeChoicesGivenWord[w], t);
        else return 1.0;
    }

    protected void updateEventTypeGivenWord(int t, int w, double prob)
    {
        if (opts.includeEventTypeGivenWord)
            update(counts.eventTypeChoicesGivenWord[w], t, prob);
    }  
    
    /**
     * Check if the rhs symbols span a potential subtree with a sentence inside.
     * This is the case if:<br>
     * - both symbols are compound, i.e. contain more than one records
     * - either symbol has a record spanning a sentence, denoted by 'SENT'
     * @param rhs1
     * @param rhs2
     * @return 
     */
    protected boolean containsSentence(Indexer<String> indexer, int rhs1, int rhs2)
    {
        String rhs1Symbol = indexer.getObject(rhs1);
        String rhs2Symbol = indexer.getObject(rhs2);
        return rhs1Symbol.contains("_") && rhs2Symbol.contains("_") ||
               (rhs1Symbol.contains("SENT") || rhs2Symbol.contains("SENT"));
    }
}
