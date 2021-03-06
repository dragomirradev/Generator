package induction.problem.event3.params;

import induction.problem.AParams;
import induction.problem.Vec;
import induction.problem.event3.discriminative.DiscriminativeEvent3Model;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Wrapper class for all the emission multinomials, i.e. Categorical Field Values,
 * None Eventype words, None Field words and generic emission words.
 * It is used in the Discriminative training framework, in order to re-sort 
 * only these particular emission probabilities using the generative baseline parameters
 * and the discriminative weights produced by local features during training.
 * 
 * @author konstas
 */
public class EmissionParams extends AParams
{
    Map<Integer, Vec> noneFieldEmissions;
    Map<Integer, Map<Integer, Vec[]>> catEmissions;
    Vec noneEventTypeEmissions, genericEmissions;
    
    public EmissionParams(DiscriminativeEvent3Model model, Map<Integer, Map<Integer, Vec[]>> catEmissions, Map<Integer, Vec> noneFieldEmissions,
                          Vec noneEventTypeEmissions, Vec genericEmissions)
    {
        super(model);
        this.catEmissions = catEmissions;
        this.noneFieldEmissions = noneFieldEmissions;
        this.noneEventTypeEmissions = noneEventTypeEmissions;
        this.genericEmissions = genericEmissions;
    }

    public Map<Integer, Map<Integer, Vec[]>> getCatEmissions()
    {
        return catEmissions;
    }

    public Map<Integer, Vec> getNoneFieldEmissions()
    {
        return noneFieldEmissions;
    }

    public Vec getGenericEmissions()
    {
        return genericEmissions;
    }

    public Vec getNoneEventTypeEmissions()
    {
        return noneEventTypeEmissions;
    }
            
    @Override
    public String output(ParamsType paramsType)
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void outputNonZero(ParamsType paramsType, PrintWriter out)
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }
        
}
