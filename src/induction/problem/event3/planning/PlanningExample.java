package induction.problem.event3.planning;

import induction.problem.event3.Event;
import induction.problem.event3.Event3Model;
import induction.problem.event3.Example;
import java.util.Map;

/**
 *
 * @author konstas
 */
public class PlanningExample extends Example
{
    private int[] eventTypeIds;
    
    public PlanningExample(Event3Model model, int[] eventTypeIds, Map<Integer, Event> events,
                           PlanningWidget trueWidget, String name) 
    {
        super(model, name, events, null, null, null, eventTypeIds.length, trueWidget);
        this.eventTypeIds = eventTypeIds;
    }

    public int[] getEventTypeIds() 
    {
        return eventTypeIds;
    }
    
}
