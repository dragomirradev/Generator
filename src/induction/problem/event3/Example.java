package induction.problem.event3;

import edu.berkeley.nlp.ling.Trees.PennTreeRenderer;
import induction.problem.event3.generative.generation.GenWidget;
import induction.problem.event3.params.Parameters;
import induction.Utils;
import induction.problem.AExample;
import induction.problem.event3.json.JsonResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

/**
 *
 * @author konstas
 */
public class Example implements AExample<Widget>
{
    public Event3Model model;
    protected String name;
    public Map<Integer, Event> events;
    public Map<Integer, List<Event>> eventsByEventType;
    protected int[] text, labels, startIndices;
    private Widget trueWidget;
    protected int[] eventTypeCounts = null;
    protected int[][] trackEvents = null;
    private final int C, N;
    protected boolean[] isPunctuationArray, isSentenceBoundaryArray;

    public Example(Event3Model model, String name, Map<Integer, Event> events, int[] text,
                   int[] labels, int[] startIndices, int N, Widget trueWidget)
    {
        this.model = model;
        this.name = name;
        this.events = events;
        this.text = text;
        this.labels = labels;
        this.startIndices = startIndices;
        this.trueWidget = trueWidget;
        this.C = model.C;
        this.N = N;
                
        String s;
        if(text != null)
        {
            isPunctuationArray = new boolean[N];
            isSentenceBoundaryArray = new boolean[N];
            for(int i = 0; i < isPunctuationArray.length; i++)
            {
                s = !model.getOpts().testInputPaths.isEmpty() || !model.getOpts().testInputLists.isEmpty() ?
                        model.testSetWordToString(text[i]) : 
                        model.wordToString(text[i]);
                isPunctuationArray[i] = 
                        // if words have pos tag attached to them
                        s.equals("./.") || s.equals(",/,") || s.equals("--/:") ||
                        s.equals("-LRB-/-LRB-") || s.equals("-RRB-/-RRB-") ||
                        (model.getOpts().andIsPunctuation && s.equals("and/CC")) ||
                        s.equals(".") || s.equals(",") || s.equals("--") ||
                        s.equals("(") || s.equals(")") ||
                        (model.getOpts().andIsPunctuation && s.equals("and"));
                
                isSentenceBoundaryArray[i] = Utils.isSentencePunctuation(s);
//                isPunctuationArray[i] = model.getOpts().posAtSurfaceLevel ?
//                        // if words have pos tag attached to them
//                        s.equals("./.") || s.equals(",/,") || s.equals("--/:") ||
//                        s.equals("-LRB-/-LRB-") || s.equals("-RRB-/-RRB-") ||
//                        (model.getOpts().andIsPunctuation && s.equals("and/CC")) :
//
//                        s.equals(".") || s.equals(",") || s.equals("--") ||
//                        s.equals("(") || s.equals(")") ||
//                        (model.getOpts().andIsPunctuation && s.equals("and"));
            }
        } 
        eventsByEventType = new HashMap<Integer, List<Event>>();
        if(events != null)
        {
            for(Event e : events.values())
            {
                Integer eventType = e.getEventTypeIndex();
                List<Event> list = eventsByEventType.get(eventType);
                if(list == null)
                {
                    list = new ArrayList<Event>();
                    eventsByEventType.put(eventType, list);
                }
                list.add(e);
            }
        }        
    }

    @Override
    public int N()
    {
        return N;
    }

    // For each original line in the input, output the widgets which were aligned
    String widgetToEvalFullString(Widget widget)
    {
        String out[] = new String[startIndices.length - 1];
        TreeSet<Integer> alignedEvents = new TreeSet();
        for(int l = 0; l < out.length; l++)
        {
            alignedEvents.clear();
            for(int i = startIndices[l]; i < startIndices[l + 1]; i++)
            {
                for(Integer e: widget.foreachEvent(i))
                {
                    if (!e.equals(Parameters.none_e))
                        alignedEvents.add(e);
                }
            } // for
            if (alignedEvents.size() > 0)
            {
                out[l] = name + "\t" + l + " " + Utils.mkString(
                        Utils.uniq(alignedEvents.toArray()), " ");
            }
        } // for
        return Utils.mkString(out, " ");
    }

    private String genPrediction(GenWidget widget)
    {
        String out = "";
        for(int i = 0; i < widget.getText().length; i++)
        {
            out += (widget.getNums()[i] > -1 ? widget.getNums()[i] : model.wordToString(widget.getText()[i])) + " ";
        }
        return out.trim();
    }
    String genWidgetToNiceFullString(GenWidget widget)
    {
        String out = name + "\n" + genPrediction(widget) +
                     "\n\n" + genWidgetToSemantics(widget) + "\n";
        if(trueWidget != null)
            out += trueWidget.performance + "\n";
        return out;
//        return out + "\n\n" + widgetToNiceFullString(widget);
    }
    
    String genCfgWidgetToNiceFullString(GenWidget widget)
    {
        String out = name + "\n" + PennTreeRenderer.render(widget.getRecordTree()) + "\n";        
        return out;
    }
    
    String genWidgetToMertFullString(GenWidget widget)
    {        
        return String.format(" ||| %s ||| ", Utils.stripTags(genPrediction(widget), model.getOpts().tagDelimiter));
//        return out + "\n\n" + widgetToNiceFullString(widget);
    }
    
    JsonResult genWidgetToJson(int i, GenWidget widget, Properties dictionary)
    {
        
        return new JsonResult(i, name, Utils.deTokenize(Utils.applyDictionary(genPrediction(widget), dictionary)), 
                              genWidgetToSemantics(widget), 
                              trueWidget != null ? trueWidget.performance : "");
    }

    String semParseWidgetToNiceFullString(GenWidget widget)
    {
        String out = name + 
                     "\n" + semParseWidgetToSemantics(widget) + "\n";
        if(trueWidget != null)
            out += trueWidget.performance + "\n";
        return out;
    }

    String genWidgetToSGMLOutput(GenWidget widget)
    {
        String out = "<doc docid=\"" + name  + "\" genre=\"nw\">\n" +
                     "<p>\n<seg id=\"1\" " +
                     "bleu=\"" + widget.getScores()[Parameters.BLEU_METRIC] + "\"" +
                     " bleu_modified=\"" + widget.getScores()[Parameters.BLEU_METRIC_MODIFIED] + "\"" +
                     " meteor=\"" + widget.getScores()[Parameters.METEOR_METRIC] + "\"" +
                     " ter=\"" + widget.getScores()[Parameters.TER_METRIC] + "\"" +
                     ">" +
                     genPrediction(widget) +
                     "</seg>\n</p>\n</doc>";

        return out;
    }

    String semParseWidgetToSemantics(GenWidget widget)
    {
        int n = widget.events[0].length;
        StringBuilder buf = new StringBuilder();
        for(int c = 0; c < widget.events.length; c++)
        {
            int i = 0;
            while (i < n) // Segment into entries
            {
                int e = widget.events[c][i];
                Event ev = events.get(e);
                int j = i + 1;
                while (j < n && widget.events[c][j] == e)
                {
                    j += 1;
                }
                if (e != Parameters.none_e)
                {
                    buf.append((e == Parameters.unreachable_e) ? "(unreachable)" : 
                        model.eventTypeToString(ev.getEventTypeIndex())).
                        append("(").append(ev.id).append(  ")[");
                }
                else
                    buf.append("(none_e) ");
                int k = i;
                while (k < j) // Segment i...j into fields
                {
                    int f = widget.fields[c][k];
                    int l = k+1;
                    while (l < j && widget.fields[c][l] == f)
                    {
                        l += 1;
                    }
                    if (k != i)
                    {
                        buf.append(" ");
                    }
                    if (f != -1)
                    {
                        buf.append(ev.fieldToString(f)).append("[");
                    }
                    for(int m = k; m < l; m++)
                    {
                        if (e != Parameters.none_e)
                        {
                            // widget.text[m] is the value of the field
                            String str = (widget.getNums()[m] > -1 ? widget.getNums()[m] :
                                f < ev.F ?
                                ev.getFields()[f].valueToString(widget.getText()[m]) : "") + "";
                            if (widget.gens != null && widget.gens[c][m] != -1)
                            {
                                str += "_" + Parameters.short_gstr[widget.gens[c][m]];
                            }
                            if (widget.numMethods != null && widget.numMethods[c][m] != -1)
                            {
                                str += Parameters.short_mstr[widget.numMethods[c][m]];
                            }
                            buf.append(str).append(" ");
                        }

                    } // for
                    buf.deleteCharAt(buf.length() - 1);
                    if (f != -1)
                    {
                        buf.append("] ");
                    }
                    k = l;
                }
                if (e != Parameters.none_e)
                {
                    buf.append("] ");
                }
                i = j;
            } // while
        } // for
        return buf.toString();
    }
    String genWidgetToSemantics(GenWidget widget)
    {
        int n = widget.events[0].length;
        StringBuilder buf = new StringBuilder();
        for(int c = 0; c < widget.events.length; c++)
        {
            int i = 0;
            while (i < n) // Segment into entries
            {
                int e = widget.events[c][i];
                Event ev = events.get(e);
                int j = i + 1;
                while (j < n && widget.events[c][j] == e)
                {
                    j += 1;
                }
                if (e != Parameters.none_e)
                {
                    buf.append((e == Parameters.unreachable_e) ? "(unreachable)" :
                        model.eventTypeToString(ev.getEventTypeIndex())).
                        append("(").append(ev.id).append(  ")[");
                }
                if (widget.fields == null)
                {
                    for(int k = i; k < j; k++)
                    {
                        buf.append(model.wordToString(widget.getText()[k])).append(" ");
                    }
                    buf.deleteCharAt(buf.length() - 1);
                } // if
                else
                {
                    int k = i;
                    while (k < j) // Segment i...j into fields
                    {
                        int f = widget.fields[c][k];
                        int l = k+1;
                        while (l < j && widget.fields[c][l] == f)
                        {
                            l += 1;
                        }
                        if (k != i)
                        {
                            buf.append(" ");
                        }
                        if (f != -1)
                        {
                            buf.append(ev.fieldToString(f)).append("[");
                        }
                        for(int m = k; m < l; m++)
                        {
                            String str = (widget.getNums()[m] > -1 ? widget.getNums()[m] :
                                model.wordToString(widget.getText()[m])) + "";
                            if (widget.gens != null && widget.gens[c][m] != -1)
                            {
                                str += "_" + Parameters.short_gstr[widget.gens[c][m]];
                            }
                            if (widget.numMethods != null && widget.numMethods[c][m] != -1)
                            {
                                str += Parameters.short_mstr[widget.numMethods[c][m]];
                            }
                            buf.append(str).append(" ");
                        }
                        buf.deleteCharAt(buf.length() - 1);
                        if (f != -1)
                        {
                            buf.append("] ");
                        }
                        k = l;
                    }
                } // else
                if (e != Parameters.none_e)
                {
                    buf.append("] ");
                }
                i = j;
            } // while
        } // for
        return buf.toString();
    }

    String widgetToNiceFullString(Widget widget)
    {
        // Returns a string on one line; use tabs later to separate
        int n = Utils.same(N(), widget.events[0].length);
        StringBuffer buf = new StringBuffer();
        buf.append(name).append(":");

        // This is rough (do it for entire example)

        // track -> set of events that go on that track
        HashSet<Integer>[] trueEvents = new HashSet[C];
        for(int i = 0; i < C; i++)
        {
            trueEvents[i] = new HashSet();
        }
        if (trueWidget != null)
        {
            for(int i = 0; i < n; i++)
            {
                for(Integer e: trueWidget.foreachEvent(i))
                {
                    if(Parameters.isRealEvent(e))
                    {
                        for(int c = 0; c < C; c++)
                        {                            
//                            if (model.eventTypeAllowedOnTrack[c].contains(
//                                    events[e].getEventTypeIndex()))
                            if (model.eventTypeAllowedOnTrack[c].contains(
                                    trueWidget.eventTypeIndices.get(e)))
                            {
                                trueEvents[c].add(e);
                            }
                        } // for
                    } // if
                } // for
            } // for
        } // if

        buf.append("\t- Pred:");
        renderWidget(widget, false, n, trueEvents, buf); // Prediction
        if (trueWidget != null) // Truth
        {
            buf.append("\t- True:");
            renderWidget(trueWidget, false, n, trueEvents, buf);
            buf.append("\t").append(trueWidget.performance).append(" (").
                    append(events.size()).append(" possible events)");
            /*if (trueWidget.eventPosterior != null)
                buf.append("\t" + trueWidget.eventPosteriorStr(events));*/
        }
        return buf.toString();
    }

    // If we propose event e on track c, is that correct?
    private boolean isOkay(int c, int e, HashSet<Integer>[] trueEvents)
    {
        return trueWidget == null || ((e == Parameters.none_e) ?
            trueEvents[c].isEmpty() : trueEvents[c].contains(e));
    }

    private void renderWidget(Widget widget, boolean printUnused, int n,
                              HashSet<Integer>[] trueEvents, StringBuffer buf)
    {
//        boolean[] used = new boolean[events.size()];
        List<Integer> used = new ArrayList<Integer>(events.size());
        for(int c = 0; c < widget.events.length; c++)
        {
            int i = 0;
            while (i < n) // Segment into entries
            {
                int e = widget.events[c][i];
                Event ev = events.get(e);
                if (Parameters.isRealEvent(e))
                {
//                    used[e] = true;
                    used.add(e);
                }
                int j = i + 1;
                while (j < n && widget.events[c][j] == e)
                {
                    j += 1;
                }
                buf.append("\t").append((widget == trueWidget || 
                        isOkay(c, e, trueEvents)) ? "" : "*").append("[TRACK").
                        append(c).append("] ");
                if (e != Parameters.none_e)
                {
                    buf.append((e == Parameters.unreachable_e) ? 
                        "(unreachable)" : ev).append( "[");
                }
                if (widget.fields == null || !Parameters.isRealEvent(e))
                {
                    for(int k = i; k < j; k++)
                    {
                        buf.append(model.wordToString(text[k])).append(" ");
                    }
                    buf.deleteCharAt(buf.length() - 1);
                } // if
                else
                {
                    int k = i;
                    while (k < j) // Segment i...j into fields
                    {
                        int f = widget.fields[c][k];
                        int l = k+1;
                        while (l < j && widget.fields[c][l] == f)
                        {
                            l += 1;
                        }
                        if (k != i)
                        {
                            buf.append(" ");
                        }
                        if (f != -1)
                        {
                            buf.append(ev.fieldToString(f)).append("[");
                        }
                        for(int m = k; m < l; m++)
                        {
                            String str = model.wordToString(text[m]);
                            if (widget.gens != null && widget.gens[c][m] != -1)
                            {
                                str += "_" + Parameters.short_gstr[widget.gens[c][m]];
                            }
                            if (widget.numMethods != null && widget.numMethods[c][m] != -1)
                            {
                                str += Parameters.short_mstr[widget.numMethods[c][m]];
                            }
                            buf.append(str).append(" ");
                        }
                        buf.deleteCharAt(buf.length() - 1);
                        if (f != -1)
                        {
//                            buf.setCharAt(buf.length()-1, ']');// append("]");
                            buf.append("]");
                        }
                        k = l;
                    }
                } // else
                if (e != Parameters.none_e)
                {
//                    buf.setCharAt(buf.length()-1, ']');
                    buf.append("]");
                }
                i = j;
            } // while
        } // for

        // Print out unused events
        if (printUnused)
        {
            for(Event ev : events.values())
            {
                if (!used.contains(ev.id))
//                    buf.append(Utils.fmts("\t%s[]", events.get(e)));
                    buf.append(Utils.fmts("\t%s[]", ev));
            } // for
        } // if
    }

    // Compute number of events of each type we have
    void computeEventTypeCounts()
    {
        eventTypeCounts = new int[model.getT()];
        for(Event event : events.values())
//        for(int i = 0; i < events.length && events[i] != null; i++)
        {
            eventTypeCounts[event.getEventTypeIndex()]++;
        }
    }

    // Set up trackEvents: for each track
//    void computeTrackEvents()
//    {
//        trackEvents = new int[C][events.size()];
//        for(int c = 0; c < C; c++)
//        {
////            for(int e = 0; e < events.length && events[e] != null; e++)
//            for(Event ev : events.values())
//            {
//                if(model.eventTypeAllowedOnTrack[c].contains(
//                        ev.getEventTypeIndex()))
//                {
////                    trackEvents[c][e] = e; // not sure
//                    trackEvents[c][e] = ev.id; // id instead of index in the array
//                }
//            } // for
//        } // for
//    }

    @Override
    public Widget getTrueWidget()
    {
        return trueWidget;
    }

    @Override
    public int[] getText()
    {
        return text;
    }

    public String[] getTextString()
    {
        String[] str = new String[text.length];
        for(int i = 0; i < text.length; i++)
        {
            str[i] = model.wordToString(text[i]);
        }
        return str;
    }
    
    public int[] getLabels()
    {
        return labels;
    }

    public int[] getStartIndices()
    {
        return startIndices;
    }

    public int[] getEventTypeCounts()
    {
        return eventTypeCounts;
    }

    public boolean[] getIsPunctuationArray()
    {
        return isPunctuationArray;
    }

    public boolean[] getIsSentenceBoundaryArray()
    {
        return isSentenceBoundaryArray;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String toString()
    {
        return name;
    }


}
