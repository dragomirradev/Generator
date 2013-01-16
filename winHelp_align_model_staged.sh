#!/bin/bash

input=$1
output=$2
numIters=$3
numThreads=$4
stagedParamsFile=$5

java -Xmx1800m -cp dist/Generator.jar:dist/lib/Helper.jar:dist/lib/kylm.jar:dist/lib/meteor.jar:dist/lib/tercom.jar:\dist/lib/srilmWrapper:\
dist/stanford-postagger-2010-05-26.jar induction.runtime.Induction \
-create \
-modeltype event3 \
-inputLists ${input} \
-examplesInSingleFile \
-execDir ${output} \
-overwriteExecDir \
-Options.stage1.numIters ${numIters} \
-inputFileExt events \
-numThreads ${numThreads} \
-stagedParamsFile ${stagedParamsFile}/stage1.params.obj.gz \
-initType staged \
-disallowConsecutiveRepeatFields \
-dontCrossPunctuation \
-initNoise 0 \
-Options.stage1.smoothing 0.01 \
-fixedGenericProb 0 \
-useStopNode \
-outputFullPred \
-dontOutputParams
#-indepEventTypes 0,10 \
#-indepFields 0,5 \
#-newEventTypeFieldPerWord 0,5 \
#-newFieldPerWord 0,5 \
#-indepWords 0,5

#-posAtSurfaceLevel \
#-inputPosTagged

#-allowConsecutiveEvents \
#-allowNoneEvent \
#-modelUnkWord \