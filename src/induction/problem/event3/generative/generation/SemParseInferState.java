package induction.problem.event3.generative.generation;

import edu.uci.ics.jung.graph.Graph;
import fig.basic.Indexer;
import induction.Hypergraph;
import induction.ngrams.NgramModel;
import induction.problem.AModel;
import induction.problem.InferSpec;
import induction.problem.Pair;
import induction.problem.event3.Constants;
import induction.problem.event3.Event;
import induction.problem.event3.Event3Model;
import induction.problem.event3.Example;
import induction.problem.event3.Field;
import induction.problem.event3.NumField;
import induction.problem.event3.Widget;
import induction.problem.event3.nodes.CatFieldValueNode;
import induction.problem.event3.nodes.TrackNode;
import induction.problem.event3.nodes.WordNode;
import induction.problem.event3.params.CatFieldParams;
import induction.problem.event3.params.EventTypeParams;
import induction.problem.event3.params.Parameters;
import induction.problem.event3.params.Params;
import induction.problem.event3.params.TrackParams;
import java.util.HashMap;

/**
 *
 * @author konstas
 */
public class SemParseInferState extends GenInferState
{
    public SemParseInferState(Event3Model model, Example ex, Params params,
            Params counts, InferSpec ispec, NgramModel ngramModel)
    {
        super(model, ex, params, counts, ispec, ngramModel);
    }

    public SemParseInferState(Event3Model model, Example ex, Params params,
            Params counts, InferSpec ispec, Graph graph)
    {
        super(model, ex, params, counts, ispec, null, graph);
    }

    @Override
    protected void initInferState(AModel model, int textLength)
    {
        super.initInferState(model, textLength);
        words = ex.getText();
        nums = new int[words.length];
        for(int w = 0; w < nums.length; w++)
        {
            nums[w] = Constants.str2num(((Event3Model)model).wordToString(words[w]));
        }
        labels = ex.getLabels();
        // map all field values to an Indexer
        vocabulary = new Indexer<String>();
        for(Event e : ex.events.values())
        {
            for(Field f : e.getFields())
            {
                if (f instanceof NumField)
                    vocabulary.add("<num>");
                else
                {
                    for(int i = 0; i < f.getV(); i++)
                    {
                        vocabulary.add(Event3Model.processWord(f.valueToString(i), opts.stemAll, opts.lemmatiseAll).toLowerCase());
                    }
                }
            }
        }
        vocabulary.add("(none)");
    }

//    @Override
//    protected void createHypergraph(Hypergraph<Widget> hypergraph)
//    {
//        // setup hypergraph preliminaries
//        hypergraph.setupForSemParse(opts.debug, opts.modelType, true, opts.kBest,
//                opts.reorderType, opts.allowConsecutiveEvents,
//                /*add NUM category and ELIDED_SYMBOL to word vocabulary. Useful for the LM calculations*/
//                vocabulary.getIndex("<num>"),
//                vocabulary.getIndex("ELIDED_SYMBOL"),
//                opts.ngramWrapper != Options.NgramWrapper.roark,
//                ((Event3Model)inferState).getWordIndexer(), ex, graph);
//
//        if(opts.fullPredRandomBaseline)
//        {
//            this.hypergraph.addEdge(hypergraph.prodStartNode(), genEvents(0, ((Event3Model)inferState).none_t()),
//                           new Hypergraph.HyperedgeInfo<Widget>()
//            {
//                public double getWeight()
//                {
//                    return 1;
//                }
//                public void setPosterior(double prob)
//                { }
//                public Widget choose(Widget widget)
//                {
//                    return widget;
//                }
//            });
//        } // if
//        else
//        {
//            this.hypergraph.addEdge(hypergraph.sumStartNode(), genEvents(0, ((Event3Model)inferState).none_t()),
//                           new Hypergraph.HyperedgeInfo<Widget>()
//            {
//                public double getWeight()
//                {
//                    return 1;
//                }
//                public void setPosterior(double prob)
//                { }
//                public Widget choose(Widget widget)
//                {
//                    return widget;
//                }
//            });
//        } // else
//    }

    @Override
    protected Widget newWidget()
    {
        HashMap<Integer, Integer> eventTypeIndices =
                            new HashMap<Integer, Integer>(ex.events.size());
        for(Event e : ex.events.values())
        {
            eventTypeIndices.put(e.getId(), e.getEventTypeIndex());
        }
        return new SemParseWidget(newMatrix(), newMatrix(), newMatrix(), newMatrix(),
                               newMatrixOne(),
                               ((Event3Model)model).eventTypeAllowedOnTrack, eventTypeIndices);
    }

    @Override
    protected Object genNumFieldValue(final int i, final int c, int event, int field)
    {
        if (nums[i] == Constants.NaN)
            return hypergraph.invalidNode; // Can't generate if not a number
        else
            return genNumFieldValue(i, c, event, field, nums[i]);
    }

    @Override
    protected CatFieldValueNode genCatFieldValueNode(final int i, int c, final int event, final int field)
    {
        CatFieldValueNode node = new CatFieldValueNode(i, c, event, field);
        if(hypergraph.addSumNode(node))
        {
            final CatFieldParams fparams = getCatFieldParams(event, field);
            // Consider generating category v from words(i)
            final int w = words[i];

            hypergraph.addEdge(node, new Hypergraph.HyperedgeInfoLM<GenWidget>() {
            public double getWeight() {
                return 1.0d;
            }
            public Pair getWeightAtRank(int rank)
            {
                int length = fparams.valueEmissions[w].size();
                Pair p = rank < length ? getAtRank(fparams.valueEmissions[w], rank) :
                    getAtRank(fparams.valueEmissions[w], length-1);
                p.label = vocabulary.getIndex(ex.events.get(event).getFields()[field].
                        valueToString((Integer)p.label).toLowerCase());
//                p.value *=
//                        (w == Event3Model.getWordIndex("<unk>")? 0.1 : 1.0);
                return p;
            }
            public void setPosterior(double prob) { }
            public GenWidget choose(GenWidget widget) { return widget; }
            public GenWidget chooseWord(GenWidget widget, int word)
            {
                widget.getText()[i] = ex.events.get(event).getFields()[field].
                        parseValue(-1, vocabulary.getObject(word));
                return widget;
            }
            });
        }
        return node;
    }
    
    // Generate word at position i with event e and field f
    @Override
    protected WordNode genWord(final int i, final int c, int event, final int field)
    {
        WordNode node = new WordNode(i, c, event, field);
        final int eventTypeIndex = ex.events.get(event).getEventTypeIndex();
        final EventTypeParams eventTypeParams = params.eventTypeParams[eventTypeIndex];
        final EventTypeParams eventTypeCounts = counts.eventTypeParams[eventTypeIndex];
        final int w = words[i];


        if(hypergraph.addSumNode(node))
        {
            if(field == eventTypeParams.none_f)
            {
                hypergraph.addEdge(node, new Hypergraph.HyperedgeInfoLM<GenWidget>() {
                    public double getWeight() {
                        return 1.0;
                    }
                    public void setPosterior(double prob) { }
                    public GenWidget choose(GenWidget widget) { return widget; }
                    public Pair getWeightAtRank(int rank)
                    {
                        Pair p = getAtRank(eventTypeParams.noneFieldEmissions, rank);
//                        p.label = vocabulary.getIndex("(none)");
                        p.label = null;
                        return p;
                    }
                    public GenWidget chooseWord(GenWidget widget, int word)
                    {
                        widget.getText()[i] = -1;
                        return widget;
                    }
                });
            } // if
            else
            {
                // G_FIELD_VALUE: generate based on field value
                hypergraph.addEdge(node, genFieldValue(i, c, event, field),
                        new Hypergraph.HyperedgeInfo<Widget>() {
                public double getWeight() {
                    return get(eventTypeParams.genChoices[field], Parameters.G_FIELD_VALUE);
                }
                public void setPosterior(double prob) {
                    update(eventTypeCounts.genChoices[field], Parameters.G_FIELD_VALUE, prob);
                }
                public Widget choose(Widget widget) {
                    widget.getGens()[c][i] = Parameters.G_FIELD_VALUE;
                    return widget;
                }
                });
                // G_FIELD_GENERIC: generate based on event type  

//                hypergraph.addEdge(node, new Hypergraph.HyperedgeInfoLM<GenWidget>() {
//                    public double getWeight() {
//                        return 1.0;
//                    }
//                    public void setPosterior(double prob) { }
//                    public GenWidget choose(GenWidget widget) { return widget; }
//                    public Pair getWeightAtRank(int rank)
//                    {
//                        Pair p =  getAtRank(params.genericEmissions, rank);
//                        p.value *= get(eventTypeParams.genChoices[field], Parameters.G_FIELD_GENERIC);
////                        p.label = vocabulary.getIndex("(none)");
//                        p.label = null;
//                        return p;
//                    }
//                    public GenWidget chooseWord(GenWidget widget, int word)
//                    {
////                        System.out.println("generic");
//                        widget.gens[c][i] = Parameters.G_FIELD_GENERIC;
//                        widget.text[i] = -1;
//                        return widget;
//                    }
//                });
            } // else
        }
        return node;
    }

    @Override
    protected WordNode genNoneWord(final int i, final int c)
    {
        WordNode node = new WordNode(i, c, ((Event3Model)model).none_t(), -1);
        if(hypergraph.addSumNode(node))
        {
            hypergraph.addEdge(node, new Hypergraph.HyperedgeInfoLM<GenWidget>() {
                public double getWeight() { return 1.0; }
                public Pair getWeightAtRank(int rank)
                {
//                    return getAtRank(params.trackParams[c].getNoneEventTypeEmissions(), rank);
                    Pair p = getAtRank(params.trackParams[c].getNoneEventTypeEmissions(), rank);
//                    p.label = vocabulary.getIndex("(none)");
                    p.label = null;
                    return p;
                }
                public void setPosterior(double prob) { }
                public GenWidget choose(GenWidget widget) { return widget; }
                public GenWidget chooseWord(GenWidget widget, int word)
                {
                    widget.getText()[i] = -1;
                    return widget;
                }
            });
        }
        return node;
    }

    // Generate track c in i...j (t0 is previous event type for track 0);
    // allowNone and allowReal specify what event types we can use
    protected TrackNode genTrack(final int i, final int j, final int t0, final int c,
                       boolean allowNone, boolean allowReal)
    {
//        TrackNode node = new TrackNode(i, j, t0, c, allowNone, allowReal);
        TrackNode node = new TrackNode(i, j, t0, c);
        final TrackParams cparams = params.trackParams[c];
        // WARNING: allowNone/allowReal might not result in any valid nodes
        if(hypergraph.addSumNode(node))
        {
            // (1) Choose the none event
          if (allowNone && (!trueInfer || ex.getTrueWidget() == null ||
              ex.getTrueWidget().hasNoReachableContiguousEvents(i, j, c)))
          {
              final int remember_t = t0; // Don't remember none_t (since [if] t == none_t, skip t)
              Object recurseNode = (c == 0) ? genEvents(j, remember_t) : hypergraph.endNode;
              hypergraph.addEdge(node,
                  genNoneEvent(i, j, c), recurseNode,
                  new Hypergraph.HyperedgeInfoLM<GenWidget>() {
                    public double getWeight() {
                          return get(cparams.getEventTypeChoices()[t0], ((Event3Model)model).none_t());
                    }
                    public void setPosterior(double prob) {}
                    public GenWidget choose(GenWidget widget) {
                      for(int k = i; k < j; k++)
                      {
                          widget.getEvents()[c][k] = Parameters.none_e;
                      }
                      return widget;
                    }
                    @Override
                    public Pair getWeightAtRank(int rank)
                    {
                        return new Pair(getWeight(), vocabulary.getIndex("none_e"));
                    }
                    @Override
                    public GenWidget chooseWord(GenWidget widget, int word)
                    {
                        return widget;
                    }
                  });
          } // if
          // (2) Choose an event type t and event e for track c
          for(final Event e : ex.events.values())
          {
              final int eventId = e.getId();
              final int eventTypeIndex = e.getEventTypeIndex();
              if (allowReal &&
                      (!trueInfer || ex.getTrueWidget() == null ||
                      ex.getTrueWidget().hasContiguousEvents(i, j, eventId)))
              {
                  final int remember_t = (indepEventTypes()) ? ((Event3Model)model).none_t() : eventTypeIndex;
                  final Object recurseNode = (c == 0) ? genEvents(j, remember_t) : hypergraph.endNode;                  
                  hypergraph.addEdge(node,
                  genEvent(i, j, c, eventId), recurseNode,
                  new Hypergraph.HyperedgeInfoLM<GenWidget>() {
                        public double getWeight()
                        {
                          if(prevIndepEventTypes())
                              return get(cparams.getEventTypeChoices()[((Event3Model)model).none_t()],
                                      eventTypeIndex) *
                                      (1.0d/(double)ex.getEventTypeCounts()[eventTypeIndex]); // remember_t = t under indepEventTypes
                          else
                              return get(cparams.getEventTypeChoices()[t0], eventTypeIndex) *
                                      (1.0/(double)ex.getEventTypeCounts()[eventTypeIndex]);
                        }
                        public void setPosterior(double prob) {}
                        public GenWidget choose(GenWidget widget) {
                          for(int k = i; k < j; k++)
                          {
                              widget.getEvents()[c][k] = eventId;
                          }
                          return widget;
                        }
                        @Override
                        public Pair getWeightAtRank(int rank)
                        {
                            return new Pair(getWeight(),
                                    new Integer((Integer)vocabulary.getIndex(
                                    e.getEventTypeName().toLowerCase())));
                        }
                        @Override
                        public GenWidget chooseWord(GenWidget widget, int word)
                        {
                            return widget;
                        }
                  });
              } // if
          } // for
        } // if
        return node;
    }
}