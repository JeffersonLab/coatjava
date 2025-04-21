#!/usr/bin/env python
import random,math,sys

mp = 0.93827
me = 0.00051
fmt = ' '.join(6*['%d']+8*['%.4f'])

def cartesian(p,t,h,m):
    x = p * math.cos(h) * math.sin(t)
    y = p * math.sin(h) * math.sin(t)
    z = p * math.cos(t)
    e = math.sqrt(p*p + m*m)
    return [x,y,z,e]

for i in range(int(sys.argv[1])):
    p = 0.1+0.1*random.random()
    e = math.sqrt(p*p+mp*mp)
    t = math.radians(70+40*random.random())
    h = 2*math.pi*random.random()
    z = -5+10*random.random()
    header = [2,1,1,0,0,0,0,0,0,0]
    electron = [1,0,1,11,0,0] + cartesian(9.0,math.radians(12),0,me) + [me,0,0,z]
    hadron = [2,0,1,2212,0,0] + cartesian(p,t,h,mp) + [mp,0,0,z]
    print(' '.join([str(x) for x in header]))
    print(fmt%tuple(electron))
    print(fmt%tuple(hadron))
