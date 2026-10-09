import sys, math
from PIL import Image, ImageDraw
name=sys.argv[1]; out=sys.argv[2]; sel=[int(x) for x in sys.argv[3].split(',')] if len(sys.argv)>3 else None
scenes={}
cur=None
for line in open('/tmp/claude-0/fell/frames.txt'):
    t=line.split()
    if t[0]=='SCENE': cur={'name':t[1],'dx':float(t[2]),'dz':float(t[3]),'cx':int(t[4]),'cz':int(t[5]),'frames':[]}; scenes[t[1]]=cur
    elif t[0]=='FRAME': cur['frames'].append((float(t[2]),[]))
    elif t[0] in ('W','L'): cur['frames'][-1][1].append((t[0],int(t[1]),int(t[2]),int(t[3])))
sc=scenes[name]; fr=sc['frames']
idx=sel if sel else list(range(len(fr)))
S=6
allc=[c for f in fr for c in f[1]]
def proj(c): 
    x=c[1]-sc['cx']; z=c[3]-sc['cz']
    return x*sc['dx']+z*sc['dz'], c[2]
al=[proj(c) for c in allc]
minA=min(a for a,_ in al)-3; maxA=max(a for a,_ in al)+3; minY=-3; maxY=max(y for _,y in al)+2
W=int((maxA-minA)*S); H=int((maxY-minY)*S)
cols=min(len(idx),3); rows=(len(idx)+cols-1)//cols
img=Image.new('RGB',(W*cols,H*rows),(200,220,240))
for n,i in enumerate(idx):
    ox=(n%cols)*W; oy=(n//cols)*H
    d=ImageDraw.Draw(img)
    gy=int((maxY-0)*S)
    d.rectangle([ox,oy+gy,ox+W,oy+H],fill=(110,100,80))
    f=fr[i]
    for kind in ('L','W'):
        for c in f[1]:
            if c[0]!=kind: continue
            a,y=proj(c); px=ox+int((a-minA)*S); py=oy+int((maxY-y-1)*S)
            d.rectangle([px,py,px+S-1,py+S-1],fill=(40,130,40) if kind=='L' else (90,50,20))
    d.text((ox+4,oy+4),f"{i}: {f[0]:.0f} deg",fill=(0,0,0))
img.save(out); print(img.size)
