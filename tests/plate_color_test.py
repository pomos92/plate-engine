"""번호판 바탕색 판별(흰/파랑=전기차/노랑) 시험: 조명 밝기·색온도·크기를 바꿔 가며 정확도 확인.

실행: python tests/plate_color_test.py
"""
import sys, random; import os; sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
import cv2, numpy as np
from train.synth_plates import render_plate, COLORS
from engine.vehicle_color import classify_plate_color
random.seed(1); np.random.seed(1)
# 실제 전기차 번호판은 연한 하늘색(그라데이션) -> 더 연한 색까지 포함해서 시험
EV_VARIANTS=[(70,150,220),(110,175,235),(140,195,240),(90,165,225)]
conf={}
for i in range(1200):
    want=random.choice(['white','ev','yellow'])
    while True:
        rgb,lab,col,two=render_plate()
        if col==want and not two: break
    img=rgb.astype(np.float32)
    if want=='ev':  # 색을 더 연하게 바꿔서 다시 칠함
        base=np.array(random.choice(EV_VARIANTS),np.float32)
        mask=(np.abs(img-np.array(COLORS['ev'][0])).sum(-1)<120)
        img[mask]=img[mask]*0+base*np.linspace(1.05,0.9,img.shape[1])[None,:,None][0][None].repeat(img.shape[0],0)[mask]
    # 조명: 밝기, 색온도(따뜻/차가운), 그림자
    img*=random.uniform(0.35,1.25)
    wb=np.array([random.uniform(0.85,1.15),1,random.uniform(0.85,1.15)])
    img*=wb
    img=np.clip(img+np.random.normal(0,6,img.shape),0,255).astype(np.uint8)
    bgr=cv2.cvtColor(img,cv2.COLOR_RGB2BGR)
    s=random.uniform(0.15,0.5); bgr=cv2.resize(bgr,None,fx=s,fy=s,interpolation=cv2.INTER_AREA)
    got=classify_plate_color(bgr)
    conf[(want,got)]=conf.get((want,got),0)+1
for w in ['white','ev','yellow']:
    tot=sum(v for (a,b),v in conf.items() if a==w)
    print(w, {b:v for (a,b),v in conf.items() if a==w}, f'{conf.get((w,w),0)/tot:.0%}')
