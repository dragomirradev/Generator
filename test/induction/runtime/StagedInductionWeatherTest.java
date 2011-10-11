/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package induction.runtime;

import fig.exec.Execution;
import induction.LearnOptions;
import induction.Options;
import induction.Options.InitType;
import induction.problem.event3.generative.GenerativeEvent3Model;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author konstas
 */
public class StagedInductionWeatherTest
{
    LearnOptions lopts;
    String name;
    GenerativeEvent3Model model;

    public StagedInductionWeatherTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp() 
    {
         String args = "-modelType event3 -Options.stage1.numIters 1 -testInputLists "
                + "test/testWeatherGovEvents -inputFileExt events "
//                + "gaborLists/genEvalListPathsGabor -inputFileExt events "
                + "-Options.stage1.smoothing 0.1 -initNoise 0 -initType staged "
                + "-stagedParamsFile results/output/weatherGov/alignments/"
                + "model_3_gabor/1.exec/stage1.params.obj -dontCrossPunctuation "
                + "-disallowConsecutiveRepeatFields -allowNoneEvent -useGoldStandardOnly";
        /*initialisation procedure from Induction class*/
        Options opts = new Options();
        Execution.init(args.split(" "), new Object[] {opts}); // parse input params
        model = new GenerativeEvent3Model(opts);
        model.init(InitType.staged, opts.initRandom, "");
        model.readExamples();
        model.logStats();
        opts.outputIterFreq = opts.stage1.numIters;
        lopts = opts.stage1;
        name = "stage1";
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of run method, of class Induction.
     */
    @Test
    public void testRun()
    {
        System.out.println("run");
        String targetOutput = "5 5 5 0 0 0 0 0 0 3 3 3 2 2 2 2 2";
        assertEquals(model.testStagedLearn(name, lopts).trim(), targetOutput);
    }
}