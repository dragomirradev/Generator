#!/bin/bash

#genDevListPathsGabor, genEvalListPathsGabor
inputLists=gaborLists/genDevListPathsGabor
#results/output/weatherGov/generation/1-best_reordered_eventTypes_linear_reg_cond_null
numThreads=2
stagedParamsFile=results/output/weatherGov/alignments/pos/model_3_cond_null_POS_CDNumbers/stage1.params.obj.gz
dmvModelParamsFile=results/output/weatherGov/dmv/train/weatherGov_uniformZ_initNoise_POS_100/stage1.dmv.params.obj.gz
kBest=20
interpolationFactor=0.5
execDir=results/output/weatherGov/generation/dependencies/model_3_${kBest}-best_0.01_STOP_inter${interpolationFactor}_condLM_hypRecomb_lmLEX_POS_predLength

java -Xmx4g -cp dist/Generator.jar:dist/lib/Helper.jar:dist/lib/kylm.jar:dist/lib/meteor.jar:dist/lib/tercom.jar:dist/lib/srilmWrapper:\
dist/stanford-postagger-2010-05-26.jar \
-Djava.library.path=lib/wrappers induction.runtime.Generation \
-numThreads $numThreads \
-outputFullPred -create \
-modelType generate \
-inputFileExt events \
-disallowConsecutiveRepeatFields \
-ngramWrapper srilm \
-outputExampleFreq 500 \
-allowConsecutiveEvents \
-reorderType eventType \
-maxPhraseLength 5 \
-binariseAtWordLevel \
-kBest ${kBest} \
-testInputLists ${inputLists} \
-execDir ${execDir} \
-stagedParamsFile ${stagedParamsFile} \
-dmvModelParamsFile ${dmvModelParamsFile} \
-ngramModelFile weatherGovLM/gabor-srilm-abs-3-gram.model.arpa \
-lengthPredictionFeatureType VALUES \
-lengthPredictionStartIndex 4 \
-lengthCompensation 0 \
-numAsSymbol \
-posAtSurfaceLevel \
-interpolationFactor ${interpolationFactor} \
-useDependencies

#-lengthPredictionModelFile gaborLists/lengthPrediction.values.linear-reg.model \
#-excludedEventTypes airline airport booking_class city entity fare_basis_code location transport
#-excludedFields flight.stop