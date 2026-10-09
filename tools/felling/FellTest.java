import com.wildlands.felling.*;
import com.wildlands.tree.*;
import java.io.*;
import java.util.*;

public class FellTest {
    // ---- fake world ----
    static class World implements WorldView, Collider {
        Map<Long,Integer> cells = new HashMap<>();   // key -> info flags
        java.util.function.IntBinaryOperator ground; // (x,z) -> top solid y (cells with y<=g solid)
        int groundH(int x,int z){ return ground.applyAsInt(x,z); }
        public int info(int x,int y,int z){
            Integer v = cells.get(Cells.pack(x,y,z));
            if (v != null) return v;
            return y <= groundH(x,z) ? K_SOLID : K_FREE;
        }
        public int classify(int x,int y,int z){
            int i = info(x,y,z); int k = WorldView.kind(i);
            switch(k){
                case K_FREE: return FREE;
                case K_LEAF: return FOREIGN_LEAF;
                case K_WOOD: return (i & F_THICK)!=0 ? FOREIGN_THICK : FOREIGN_THIN;
                default: return SOLID;
            }
        }
    }

    static void placeTree(World w, TreeBuilder.Species sp, int h, long seed, int ox, int oy, int oz){
        TreeModel m = TreeBuilder.generate(sp, h, seed);
        for (TreeModel.Voxel v : m.voxels()) {
            int x=ox+v.x(), y=oy+v.y(), z=oz+v.z();
            int f;
            if (v.wood()) {
                f = WorldView.K_WOOD;
                boolean full = v.cls() >= TreeModel.FULL;
                if (full) f |= WorldView.F_LOG;
                f |= v.conn() << WorldView.CONN_SHIFT;
                if (v.cls() >= 5) f |= WorldView.F_THICK;
                if (full) {
                    if (v.axis()==TreeModel.AXIS_Y) f |= WorldView.F_VERT;
                    else if (v.axis()==TreeModel.AXIS_X) f |= WorldView.F_AXIS_X;
                    else f |= WorldView.F_AXIS_Z;
                } else {
                    f |= WorldView.F_NATURAL;
                    if (v.connects(TreeModel.UP) && v.connects(TreeModel.DOWN)) f |= WorldView.F_VERT;
                }
            } else f = WorldView.K_LEAF | WorldView.F_NATURAL;
            long key = Cells.pack(x,y,z);
            Integer old = w.cells.get(key);
            if (old != null && WorldView.kind(old) == WorldView.K_WOOD) continue;   // nothing replaces wood
            if (old != null && !v.wood()) { continue; }
            w.cells.put(key, f);
        }
    }

    // ---- runs ----
    static String run(String name, World w, int cx,int cy,int cz, double awayX,double awayZ, PrintWriter out, boolean verbose){
        w.cells.remove(Cells.pack(cx,cy,cz));
        long t0=System.nanoTime();
        Piece p = TreeScan.scan(w,cx,cy,cz,2500,9000);
        if (p==null){ return name+": NO FALL ("+TreeScan.lastReason+")"; }
        FallPlanner.Choice ch = FallPlanner.choose(p, w, awayX, awayZ, 0.8);
        Fall fall = ch.fall();
        FallSim sim = new FallSim(fall, w, 1.0);
        // remove piece from world
        Map<Long,Integer> pieceInfo = new HashMap<>();
        for (int i=0;i<p.wood.length;i++){ pieceInfo.put(p.wood[i], p.woodInfo[i]); w.cells.remove(p.wood[i]); }
        for (long k : p.leaves){ pieceInfo.put(k, WorldView.K_LEAF|WorldView.F_NATURAL); w.cells.remove(k); }
        // sim uses 'own' to treat piece cells free; but the world no longer contains them, so fine either way.
        List<Frame> frames = new ArrayList<>();
        Set<Long> cur = new HashSet<>();
        // draw original
        long ops=0; int ticks=0; Frame last=null;
        int maxTicks=600;
        // initial frame (theta 0) to show
        Frame f0 = fall.frame(0); frames.add(f0); last=f0;
        for (long k : p.wood) cur.add(k); for (long k: p.leaves) cur.add(k);
        while(!sim.done() && ticks<maxTicks){
            ticks++;
            Frame f = sim.tick();
            if (f!=null){ frames.add(f); last=f;
                Set<Long> nx=new HashSet<>(); for (long c: f.cells) nx.add(c);
                for (long c: cur) if(!nx.contains(c)) ops++;
                for (long c: nx) if(!cur.contains(c)) ops++;
                cur=nx;
            }
        }
        // final state into world
        for (int i=0;i<last.cells.length;i++){
            int f = last.wood[i] ? fall.piece.woodInfo[last.src[i]] : (WorldView.K_LEAF|WorldView.F_NATURAL);
            w.cells.put(last.cells[i], f);
        }
        // invariants
        int woodN=0, leafN=0, inSolid=0; int minWoodY=999;
        Set<Long> wood = new HashSet<>();
        for (int i=0;i<last.cells.length;i++){
            long c=last.cells[i]; int x=Cells.x(c),y=Cells.y(c),z=Cells.z(c);
            if (last.wood[i]){ woodN++; wood.add(c); minWoodY=Math.min(minWoodY,y); if (y<=w.groundH(x,z)) inSolid++; }
            else { leafN++; if (y<=w.groundH(x,z)) inSolid++; }
        }
        // connectivity (26-neighbour) of final wood
        int largest=0; Set<Long> seen=new HashSet<>();
        for (long s : wood){ if(seen.contains(s)) continue; int cnt=0; ArrayDeque<Long> q=new ArrayDeque<>(); q.add(s); seen.add(s);
            while(!q.isEmpty()){ long c=q.poll(); cnt++; int x=Cells.x(c),y=Cells.y(c),z=Cells.z(c);
                for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){ long n=Cells.pack(x+dx,y+dy,z+dz); if(wood.contains(n)&&seen.add(n)) q.add(n);} }
            largest=Math.max(largest,cnt);}
        long ms=(System.nanoTime()-t0)/1000000;
        String res = String.format("%s: F wood=%d leaves=%d stump=%d dir=%.0f° dev=%.0f° rest=%.0f° ticks=%d frames=%d ops=%d | final wood=%d(%.0f%%) leaves=%d(%.0f%%) inSolid=%d conn=%d/%d speed=%.1f ms=%d",
            name, p.wood.length, p.leaves.length, p.groundedWood, Math.toDegrees(Math.atan2(fall.dirZ,fall.dirX)), ch.deviationDeg(), Math.toDegrees(sim.theta()), sim.ticks(), frames.size()-1, ops,
            woodN, 100.0*woodN/p.wood.length, leafN, 100.0*leafN/Math.max(1,p.leaves.length), inSolid, largest, woodN, sim.impactSpeed(), ms);
        if (out!=null){
            out.println("SCENE "+name+" "+fall.dirX+" "+fall.dirZ+" "+cx+" "+cz);
            int[] pick; // dump selected frames: first, ~each, last
            for (int fi=0; fi<frames.size(); fi++){
                Frame f=frames.get(fi);
                out.println("FRAME "+fi+" "+Math.toDegrees(f.theta));
                for (int i=0;i<f.cells.length;i++) out.println((f.wood[i]?"W ":"L ")+Cells.x(f.cells[i])+" "+Cells.y(f.cells[i])+" "+Cells.z(f.cells[i]));
            }
            out.println("END");
        }
        return res;
    }

    static World flat(){ World w=new World(); w.ground=(x,z)->-1; return w; }

    public static void main(String[] args) throws Exception {
        PrintWriter out = new PrintWriter(new FileWriter("/tmp/claude-0/fell/frames.txt"));
        String only = args.length>0?args[0]:null;
        // 1 flat
        for (TreeBuilder.Species sp : TreeBuilder.Species.values()) for (int h : new int[]{10,20,30}) {
            World w=flat(); placeTree(w,sp,h,12345L+h,0,0,0);
            System.out.println(run("flat_"+sp+"_"+h, w,0,0,0, 1,0, (only!=null&&only.equals("flat_"+sp+"_"+h))?out:null,false));
        }
        // uphill / downhill  (ground rises along +x)
        { World w=new World(); w.ground=(x,z)-> x>3 ? (x-3)/2 - 1 : -1; placeTree(w,TreeBuilder.Species.OAK,24,777,0,0,0);
          System.out.println(run("uphill_oak_24", w,0,0,0,1,0,(only!=null&&only.equals("uphill"))?out:null,false)); }
        { World w=new World(); w.ground=(x,z)-> x>3 ? -1-(x-3)/3 : -1; placeTree(w,TreeBuilder.Species.OAK,24,777,0,0,0);
          System.out.println(run("downhill_oak_24", w,0,0,0,1,0,(only!=null&&only.equals("downhill"))?out:null,false)); }
        // wall of rock 8 blocks ahead
        { World w=new World(); w.ground=(x,z)-> (x>=9 && x<=11) ? 12 : -1; placeTree(w,TreeBuilder.Species.OAK,24,777,0,0,0);
          System.out.println(run("wall_oak_24", w,0,0,0,1,0,(only!=null&&only.equals("wall"))?out:null,false)); }
        // dense forest
        { World w=flat(); placeTree(w,TreeBuilder.Species.OAK,22,1,0,0,0);
          int[][] nb={{6,0},{-6,2},{5,6},{-4,-6},{9,-3},{12,4},{3,-8}}; long sd=100;
          for(int[] n: nb) placeTree(w,TreeBuilder.Species.OAK,16+(int)(sd%8),sd++,n[0],0,n[1]);
          System.out.println(run("forest_oak_22", w,0,0,0,1,0,(only!=null&&only.equals("forest"))?out:null,false)); }
        // mid trunk cut at y=5
        { World w=flat(); placeTree(w,TreeBuilder.Species.OAK,24,555,0,0,0);
          System.out.println(run("midcut5_oak_24", w,0,5,0,-1,0,(only!=null&&only.equals("midcut"))?out:null,false)); }

        // ---- scan-only scenarios ----
        { // pole
          World w=flat(); for(int y=0;y<8;y++) w.cells.put(Cells.pack(0,y,0), WorldView.K_WOOD|WorldView.F_LOG|WorldView.F_THICK|WorldView.F_VERT);
          w.cells.remove(Cells.pack(0,2,0)); Piece pc=TreeScan.scan(w,0,2,0,2500,9000);
          System.out.println("pole: "+(pc==null?"NO FALL ("+TreeScan.lastReason+")":"FALL ?!")); }
        { // cabin
          World w=flat(); int L=WorldView.K_WOOD|WorldView.F_LOG|WorldView.F_THICK;
          for(int y=0;y<4;y++) for(int i=0;i<=5;i++){ for (int[] c : new int[][]{{i,0},{i,5},{0,i},{5,i}}) {
              boolean corner=(c[0]==0||c[0]==5)&&(c[1]==0||c[1]==5);
              w.cells.put(Cells.pack(c[0],y,c[1]), L | (corner?WorldView.F_VERT: (c[1]==0||c[1]==5)?WorldView.F_AXIS_X:WorldView.F_AXIS_Z)); } }
          w.cells.remove(Cells.pack(0,0,0)); Piece pc=TreeScan.scan(w,0,0,0,2500,9000);
          System.out.println("cabin corner cut: "+(pc==null?"NO FALL ("+TreeScan.lastReason+")":"FALL wood="+pc.wood.length)); }
        { // vanilla-like tree: logs only + natural leaves
          World w=flat(); for(int y=0;y<7;y++) w.cells.put(Cells.pack(0,y,0), WorldView.K_WOOD|WorldView.F_LOG|WorldView.F_THICK|WorldView.F_VERT);
          for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=4;y<=8;y++){ if(!w.cells.containsKey(Cells.pack(x,y,z))) w.cells.put(Cells.pack(x,y,z), WorldView.K_LEAF|WorldView.F_NATURAL);} 
          System.out.println(run("vanilla_like", w,0,0,0,1,0,null,false)); }
        { // dead tree (snag): wood only
          World w=flat(); TreeModel m=TreeBuilder.generate(TreeBuilder.Species.OAK,18,42L);
          for (TreeModel.Voxel v : m.voxels()) { if(!v.wood()||v.y()>14) continue; int f=WorldView.K_WOOD; boolean full=v.cls()>=TreeModel.FULL; if(full) f|=WorldView.F_LOG; f|=v.conn()<<WorldView.CONN_SHIFT; if(v.cls()>=5) f|=WorldView.F_THICK; if(full&&v.axis()==TreeModel.AXIS_Y) f|=WorldView.F_VERT; else if(!full){f|=WorldView.F_NATURAL; if(v.connects(TreeModel.UP)&&v.connects(TreeModel.DOWN)) f|=WorldView.F_VERT;} else if(v.axis()==TreeModel.AXIS_X) f|=WorldView.F_AXIS_X; else f|=WorldView.F_AXIS_Z; w.cells.put(Cells.pack(v.x(),v.y(),v.z()), f);} 
          System.out.println(run("snag_oak_18", w,0,0,0,1,0,null,false)); }
        { // fat trunk: find base footprint of a big oak
          for (long seed=1; seed<400; seed++){ World w=flat(); placeTree(w,TreeBuilder.Species.OAK,40,seed,0,0,0);
            List<long[]> base=new ArrayList<>(); for(var e: w.cells.entrySet()){ long k=e.getKey(); if(Cells.y(k)==0 && WorldView.kind(e.getValue())==WorldView.K_WOOD && (e.getValue()&WorldView.F_VERT)!=0) base.add(new long[]{k}); }
            if (base.size()>=3){
              long first=base.get(0)[0]; String partial="";
              for (int i=0;i<base.size();i++){ long k=base.get(i)[0]; w.cells.remove(k); Piece pc=TreeScan.scan(w,Cells.x(k),Cells.y(k),Cells.z(k),2500,9000);
                 partial+=(pc==null?"held ":"FALL ")+""; if (pc!=null) break; }
              System.out.println("fat trunk seed="+seed+" base="+base.size()+" chop sequence: "+partial); break; } }
        }
        out.close();
    }
}
