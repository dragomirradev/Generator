package induction.problem.event3.generative.alignment;

import induction.problem.AExample;
import induction.problem.event3.params.Parameters;
import fig.basic.EvalResult;
import fig.basic.Fmt;
import induction.Utils;
import induction.problem.APerformance;
import induction.problem.Pair;
import induction.problem.event3.Constants;
import induction.problem.event3.Event3Model;
import induction.problem.event3.Widget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

/**
 * Compute number of alignments we get right.
 * Only be sensitive to precision/recall of set of alignments from
 * one line of the input to the events
 * @author konstas
 */
public class AlignmentPerformance extends APerformance<Widget>
{
    protected  Event3Model model;
    protected  EvalResult result; // Precision/recall on events
    protected  int[][] counts;    // Confusion matrix on event types
    // For each event type, number of correct (some of counts(t)(t) could be wrong)
    protected int[] correctCounts;
    protected float totalWer = 0; // record permutations word error rate
    protected int totalCounts = 0;
    
    public AlignmentPerformance(Event3Model model)
    {
        this.model = model;
        result = new EvalResult();
        counts = new int[T() + 1][T() + 1];
        correctCounts = new int[T()];
    }

    private int T()
    {
        return model.getT();
    }

    @Override
    public void add(AExample example, Widget predWidget)
    {
        add((Widget)example.getTrueWidget(), predWidget);
    }

    @Override
    public void add(Widget trueWidget, Widget predWidget)
    {
        if(trueWidget != null)
        {
            final int[] startIndices = Utils.same(trueWidget.getStartIndices(), predWidget.getStartIndices());
            final EvalResult subResult = new EvalResult();

            for(int l = 0; l < startIndices.length-1; l++)
            {
                // Take care of unreachables: just get them wrong
                // ASSUMPTION: unreachables span the entire line l,
                // so we just need to check the first position
                // Note that there might be multiple unreachables per example
                for( Integer e: trueWidget.foreachEvent(startIndices[l]))
                {
                    if(e == Parameters.unreachable_e)
                        addResult(subResult, true, false); // Get it wrong automatically
                }
                HashSet<Integer> trueHit = computeHit(startIndices, l, trueWidget);
                HashSet<Integer> predHit = computeHit(startIndices, l, predWidget);

                // Get the things in common
                Iterator<Integer> it = trueHit.iterator();
                while(it.hasNext())
                {
                    Integer e = it.next();
                    int eventTypeIndex = trueWidget.getEventTypeIndices().get(e);
                    if(predHit.contains(e))
                    {
                        it.remove();
                        predHit.remove(e);
                        counts[eventTypeIndex][eventTypeIndex]++;
                        correctCounts[eventTypeIndex]++;
                        addResult(subResult, true, true);
                    }
                } // for

                // Record differences between two sets
                for(Integer e : trueHit)
                {
                    addResult(subResult, true, false);
                }
                for(Integer e : predHit)
                {
                    addResult(subResult, false, true);
                }
                if(trueHit.isEmpty())
                {
                    for(Integer e : predHit)
                    {
                        final int pt = predWidget.getEventTypeIndices().get(e);
                        counts[T()][pt]++;
                    }
                }
                else if(predHit.isEmpty())
                {
                    for(Integer e : trueHit)
                    {
                        final int tt = trueWidget.getEventTypeIndices().get(e);
                        counts[tt][T()]++;
                    }
                }
                else // Heuristic: mark an error on all pairs
                {
                    for(Integer e : trueHit)
                    {
                        final int tt = trueWidget.getEventTypeIndices().get(e);
                        for(Integer f : predHit)
                        {
                            final int pt = predWidget.getEventTypeIndices().get(f);
                            counts[tt][pt]++; // Note that tt = pt is possible and it is still wrong
                        }
                    }
                }
            } // for
            
            // compute record WER            
            float wer = computeWer(trueWidget, predWidget);            
           
            trueWidget.performance = subResult.toString() + getWerResult(wer, 1);
        }
    }

    private HashSet<Integer> computeHit(int[] startIndices, int l, Widget widget)
    {
        final HashSet<Integer> hit = new HashSet();
        for(int i = startIndices[l]; i < startIndices[l+1]; i++)
        {
            for(Integer e: widget.foreachEvent(i))
            {
                if(Parameters.isRealEvent(e))
                {
                    hit.add(e);
                }
            }
        }
        return hit;
    }
    
    protected Integer[] computeGoldEventsSequence(int[] startIndices, Widget widget)
    {                
        List<Integer> list = new ArrayList<Integer>();
        for(int l = 0; l < startIndices.length-1; l++)
        {
            for(Integer e: widget.foreachEvent(startIndices[l]))
            {
                if(Parameters.isRealEvent(e))
                {
                    list.add(e);
                }
            } // for
        } // for
        return list.toArray(new Integer[0]);
    }
    
    protected Integer[] computePredEventsSequence(Widget widget)
    {
        List<Integer> list = new ArrayList<Integer>();
        int prev = -5;
        for(Integer cur : widget.getEvents()[0])
        {            
            if(Parameters.isRealEvent(cur))
            {                
                if(prev != cur)
                    list.add(cur);
                prev = cur;
            }
        }
        return list.toArray(new Integer[0]);
    }
    
    protected String getWerResult(float wer, int count)
    {
        return String.format(" WER = %s", Fmt.D(count == 1 ? wer : (wer / (float)count)) );
    }
    
    protected float computeWer(Widget trueWidget, Widget predWidget)
    {
        Integer[] trueSeq = computeGoldEventsSequence(trueWidget.getStartIndices(), trueWidget);
        Integer[] predSeq = computePredEventsSequence(predWidget);
        float wer = Utils.computeWER(predSeq, trueSeq);
        totalWer += wer;
        totalCounts++;
        return wer;
    }
    
    protected void addResult(EvalResult subResult, boolean trueProbability,
                           boolean predictedProbability)
    {
        addResult(result, subResult, trueProbability, predictedProbability);
//        subResult.add(trueProbability, predictedProbability);
//        result.add(trueProbability, predictedProbability);
    }

    protected void addResult(EvalResult totalResult, EvalResult subResult, boolean trueProbability,
                           boolean predictedProbability)
    {
        subResult.add(trueProbability, predictedProbability);
        totalResult.add(trueProbability, predictedProbability);
    }

    /**
     * Print confusion matrix: true event types are columns, predicted event types are rows
     */
    @Override
    public String output()
    {
        TreeSet<Pair<String>> ts = new TreeSet<Pair<String>>();
        for(int t = 0; t < T(); t++)
        {
            ts.add(new Pair(t, model.eventTypeToString(t), Constants.Compare.LABEL));
        }
        String[][] table = new String[T() + 2][T() + 2];
        // write the headers
        table[0][0] = "";
        for(Pair<String> pair : ts)
        {
            table[0][(int)pair.value + 1] = table[(int)pair.value + 1][0] = pair.label;
        }
        table[T() + 1][0] = table[0][T() + 1] = "(NONE)";
        // write the data
        int tt = 0;
        for(Pair pairRow : ts)
        {
            tt = (int) pairRow.value;
            int pt = 0;
            for(Pair pairColumn : ts)
            {
                pt = (int) pairColumn.value;
                table[tt+1][pt+1] = ((pt == tt) ? correctCounts[tt] + "/" : "") +
                                Utils.fmt(counts[tt][pt]);

            }
            table[tt+1][T() + 1] = Utils.fmt(counts[tt][T()]);
        }
        int pt = 0;
        for(Pair pairColumn : ts)
        {
            pt = (int) pairColumn.value;
            table[T() + 1][pt + 1] = Utils.fmt(counts[T()][pt]);
        }
        table[T() + 1][T() + 1] = "";
        return "\n" + result.toString() + "\n" + 
                getWerResult(totalWer, totalCounts) + "\n" +
                Utils.formatTable(table, Constants.Justify.RIGHT);
    }

    public double getAccuracy()
    {
        return result.f1();
    }
}
