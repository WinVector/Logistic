import pandas as pd
import numpy as np
import scipy as sp
import matplotlib.pyplot as plt
from sklearn.model_selection import train_test_split

dataset= np.genfromtxt("C:\\Users\dharm\Desktop\dataset.csv", delimiter=",")

array=dataset[1:,]

x=array[0:,1:-1]

y_raw=array[0: , -1]

y=y_raw.reshape((199,1))

x_train, x_test, y_train, y_test= train_test_split(x, y, test_size=0.1,random_state=0)

input_size= x_train.shape[1]

weights= np.random.rand(input_size, 1)

print(weights.shape,input_size)

output=np.dot(x_train,weights)

def sigmoid(output):
    return 1/(1+np.exp(-output))

h=sigmoid(output)

m=len(y)

alpha=0.01


def cost(h,y):
    return(-y*np.log(h)-(1-y)*np.log(1-h)).mean

epoch=1000

for i in range(epoch):
    output=np.dot(x_train,weights)
    h=sigmoid(output)
    error= np.mean((h-y_train)**2)
    weights=weights-alpha*(1/m)*np.dot(np.transpose(x_train),(h-y_train))

op= np.dot(x_test, weights)
op= sigmoid(op)

c=0;
for i in range(op.shape[0]):
    if op[i][0]>0.91:
       op[i][0]=1;
    else:
        op[i][0]=0;
    if op[i][0]==y_test[i][0]:
        c=c+1;
print(c/op.shape[0])
