#!/bin/bash

#Fold 1
./robocup_model_3.sh robocupLists/robocupFold1PathsTrain robocupLists/robocupAllUnreachable \
results/output/robocup/model_3_percy_NO_NULL_semPar_values_unk_salience/fold1 10 2

#Fold 2
./robocup_model_3.sh robocupLists/robocupFold2PathsTrain robocupLists/robocupAllUnreachable \
 results/output/robocup/model_3_percy_NO_NULL_semPar_values_unk_salience/fold2 10 2
#Fold 3
./robocup_model_3.sh robocupLists/robocupFold3PathsTrain robocupLists/robocupAllUnreachable \
 results/output/robocup/model_3_percy_NO_NULL_semPar_values_unk_salience/fold3 10 2
#Fold 4
./robocup_model_3.sh robocupLists/robocupFold4PathsTrain robocupLists/robocupAllUnreachable \
 results/output/robocup/model_3_percy_NO_NULL_semPar_values_unk_salience/fold4 10 2
