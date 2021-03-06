package induction.problem.event3;

import induction.problem.AParams;
import induction.problem.VecFactory;
import java.io.Serializable;

/**
 *
 * @author konstas
 */
public abstract class Field implements Serializable
{
    static final long serialVersionUID = -6112633552247057265L;
    protected String name;
//    protected Event3Model model;
    protected int maxLength;

    public String getName() 
    {
        return name;
    }

    public int getMaxLength()
    {
        return maxLength;
    }
        
    public abstract int getV(); // number of possible values
    public abstract String valueToString(int v);
    public abstract int parseValue(int role, String str);    
    public abstract AParams newParams(Event3Model model, int numOfWords, VecFactory.Type vectorType, String prefix);
}
