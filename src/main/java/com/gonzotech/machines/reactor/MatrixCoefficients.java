package com.gonzotech.machines.reactor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** Deterministic, dimensionless GAME scoring. No physical nuclear data or units. */
public final class MatrixCoefficients {
    public enum Material {
        // M, Af, At, C. Fuel transmission and C are additional game conventions.
        FUEL(0, 0, 0, .10),
        WATER(.90, .05, .15, 1.00),
        GRAPHITE(.55, .01, .02, .10),
        BORON(.02, .65, .98, .10),
        BARIUM(.05, .10, .20, .15);
        final double m, af, at, c;
        Material(double m, double af, double at, double c) {
            this.m=m; this.af=af; this.at=at; this.c=c;
        }
    }

    /** The only two public output values. heat is unbounded; peakLeak is in [0,1). */
    public record Coefficients(double heat, double peakLeak) {}

    public record Rules(double sourcePerFuel, double fastHeatWeight, double scatterFraction,
                        double conductionFloor, double surfaceCooling,
                        int maxIterations, double relativeTolerance) {
        public Rules {
            if (!positive(sourcePerFuel) || !Double.isFinite(fastHeatWeight) || fastHeatWeight<0 || fastHeatWeight>1
                || !Double.isFinite(scatterFraction) || scatterFraction<0 || scatterFraction>=1
                || !positive(conductionFloor) || !positive(surfaceCooling)
                || maxIterations<1 || !positive(relativeTolerance) || relativeTolerance>1e-3)
                throw new IllegalArgumentException("Invalid game rules");
        }
        public static Rules defaults() { return new Rules(1, .05, .15, .05, 1, 2000, 1e-10); }
    }

    /** Fixed for the game, not recomputed when the player's construction changes. */
    public record Calibration(double heatBudget, double leakHalfScale) {
        public Calibration {
            if (!positive(heatBudget) || !positive(leakHalfScale))
                throw new IllegalArgumentException("Calibration values must be positive and finite");
        }
    }

    public static final class Grid {
        public final int nx, ny, nz, size;
        private final Material[] cells;
        /** Per-cell model source weight. Dry structural diagnostics use one fixed unit per fuel sheath. */
        private final double[] fuelStrengths;
        public Grid(int nx, int ny, int nz, Material fill) {
            if (nx<1 || ny<1 || nz<1) throw new IllegalArgumentException("Dimensions must be positive");
            Objects.requireNonNull(fill);
            this.nx=nx; this.ny=ny; this.nz=nz;
            this.size=Math.multiplyExact(Math.multiplyExact(nx,ny),nz);
            // Face diagnostics use a 6*N array; reject overflow before allocating.
            Math.multiplyExact(size,6);
            cells=new Material[size]; Arrays.fill(cells,fill);
            fuelStrengths=new double[size];
            if(fill==Material.FUEL)Arrays.fill(fuelStrengths,1.0);
        }
        public int index(int x,int y,int z) {
            if (!inside(x,y,z)) throw new IndexOutOfBoundsException();
            return x+nx*(y+ny*z);
        }
        public boolean inside(int x,int y,int z) {
            return x>=0&&x<nx&&y>=0&&y<ny&&z>=0&&z<nz;
        }
        public Grid set(int x,int y,int z,Material material) {
            int i=index(x,y,z);
            cells[i]=Objects.requireNonNull(material);
            fuelStrengths[i]=material==Material.FUEL?1.0:0.0;
            return this;
        }
        /** Set the amount of TVEL signal emitted by this FUEL cell; zero is a passive sheath. */
        public Grid setFuelStrength(int x,int y,int z,double strength) {
            int i=index(x,y,z);
            if(cells[i]!=Material.FUEL)throw new IllegalStateException("Fuel strength requires a FUEL cell");
            if(!Double.isFinite(strength)||strength<0)throw new IllegalArgumentException("Fuel strength must be nonnegative and finite");
            fuelStrengths[i]=strength;
            return this;
        }
        public Material get(int x,int y,int z) { return cells[index(x,y,z)]; }
        public double fuelStrength(int x,int y,int z) { return fuelStrengths[index(x,y,z)]; }
        public Grid copy() {
            Grid out=new Grid(nx,ny,nz,Material.WATER);
            System.arraycopy(cells,0,out.cells,0,size);
            System.arraycopy(fuelStrengths,0,out.fuelStrengths,0,size);
            return out;
        }
    }

    static final class Direction {
        final int x,y,z;
        final double length;
        Direction(int x,int y,int z) { this.x=x;this.y=y;this.z=z;length=Math.sqrt(x*x+y*y+z*z); }
    }
    static final Direction[] DIRECTIONS;
    static {
        DIRECTIONS=new Direction[26];int n=0;
        for(int z=-1;z<=1;z++)for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)
            if(x!=0||y!=0||z!=0)DIRECTIONS[n++]=new Direction(x,y,z);
    }

    private final Rules rules;
    private final Calibration calibration;
    public MatrixCoefficients(Rules rules, Calibration calibration) {
        this.rules=Objects.requireNonNull(rules); this.calibration=Objects.requireNonNull(calibration);
    }
    public Calibration calibration() { return calibration; }

    public static Grid referenceLayout() {
        Grid g=new Grid(3,3,3,Material.WATER);
        for(int y=0;y<3;y++)for(int x=0;x<3;x++)
            g.set(x,y,1,((x+y)%2==1)?Material.FUEL:Material.GRAPHITE);
        return g;
    }
    public static Calibration calibrate(Rules rules, Grid reference, double targetHeat, double leakHalfScale) {
        if(!positive(targetHeat))throw new IllegalArgumentException("targetHeat must be positive");
        Raw r=analyze(reference,rules);
        if(r.peakLoad<=0)throw new IllegalArgumentException("Reference must contain an emitting fuel cell");
        return new Calibration(r.peakLoad/targetHeat,leakHalfScale);
    }
    public static MatrixCoefficients balancedDefault() {
        Rules r=Rules.defaults();
        return new MatrixCoefficients(r,calibrate(r,referenceLayout(),.70,.10));
    }
    public Coefficients evaluate(Grid grid) {
        Raw r=analyze(Objects.requireNonNull(grid),rules);
        return new Coefficients(r.peakLoad/calibration.heatBudget,
                r.peakEscape/(r.peakEscape+calibration.leakHalfScale));
    }

    // Internal fields are retained here only for validation and debugging.
    static record Raw(double[] heatProduction,double[] load,double[] escapeFaces,
                      double emitted,double absorbed,double escaped,double peakLoad,double peakEscape,
                      int hotCell,int leakCell,int leakFace,int transportIterations,int thermalIterations,double residual,int edges) {}

    static Raw analyze(Grid g,Rules r) {
        int n=g.size;
        double[] heat=new double[n], fast=new double[n], slow=new double[n], escapeFaces=new double[n*6];
        double emitted=0,absorbed=0,escaped=0;
        for(int i=0;i<n;i++)if(g.cells[i]==Material.FUEL)emitted+=r.sourcePerFuel*g.fuelStrengths[i];
        if(emitted==0)return new Raw(heat,new double[n],escapeFaces,0,0,0,0,0,-1,-1,-1,0,0,0,0);

        // Fixed-point iteration for isotropic scattered sources. Each directional
        // sweep is exact for the current scattered source and preserves forward motion.
        double[] scatterF=new double[n],scatterT=new double[n],sumF=new double[n],sumT=new double[n];
        int transportIterations=0;boolean converged=false;
        for(int pass=0;pass<r.maxIterations;pass++) {
            transportIterations=pass+1;
            Arrays.fill(heat,0);Arrays.fill(escapeFaces,0);Arrays.fill(sumF,0);Arrays.fill(sumT,0);
            absorbed=0;escaped=0;
            for(Direction d:DIRECTIONS) {
                double[] surviveF=new double[Material.values().length],surviveT=surviveF.clone(),moderate=surviveF.clone();
                for(Material m:Material.values()) {
                    surviveF[m.ordinal()]=Math.pow(1-m.af,d.length);
                    surviveT[m.ordinal()]=Math.pow(1-m.at,d.length);
                    moderate[m.ordinal()]=1-Math.pow(1-m.m,d.length);
                }
                for(int zz=0;zz<g.nz;zz++)for(int yy=0;yy<g.ny;yy++)for(int xx=0;xx<g.nx;xx++) {
                    int x=d.x<0?g.nx-1-xx:xx, y=d.y<0?g.ny-1-yy:yy, z=d.z<0?g.nz-1-zz:zz;
                    int i=x+g.nx*(y+g.ny*z),px=x-d.x,py=y-d.y,pz=z-d.z;
                    double f=0,t=0;
                    if(g.inside(px,py,pz)) { int prev=px+g.nx*(py+g.ny*pz);f=fast[prev];t=slow[prev]; }
                    Material m=g.cells[i];int mi=m.ordinal();
                    if(m==Material.FUEL)f+=r.sourcePerFuel*g.fuelStrengths[i]/26.;
                    double fs=f*surviveF[mi],ts=t*surviveT[mi];
                    absorbed+=(f-fs)+(t-ts);
                    double converted=fs*moderate[mi];
                    double uf=fs-converted,ut=ts+converted;
                    sumF[i]+=uf;sumT[i]+=ut;
                    // Exposure score, NOT an energy-conservation equation.
                    heat[i]+=d.length*(r.fastHeatWeight*uf+ut);
                    fast[i]=(1-r.scatterFraction)*uf+scatterF[i];
                    slow[i]=(1-r.scatterFraction)*ut+scatterT[i];

                    int nx=x+d.x,ny=y+d.y,nz=z+d.z;
                    if(!g.inside(nx,ny,nz)) {
                        double out=fast[i]+slow[i];escaped+=out;
                        int crossed=(nx<0||nx>=g.nx?1:0)+(ny<0||ny>=g.ny?1:0)+(nz<0||nz>=g.nz?1:0);
                        double portion=out/crossed;
                        if(nx<0)escapeFaces[6*i]+=portion;
                        if(nx>=g.nx)escapeFaces[6*i+1]+=portion;
                        if(ny<0)escapeFaces[6*i+2]+=portion;
                        if(ny>=g.ny)escapeFaces[6*i+3]+=portion;
                        if(nz<0)escapeFaces[6*i+4]+=portion;
                        if(nz>=g.nz)escapeFaces[6*i+5]+=portion;
                    }
                }
            }
            double difference=0,normScatter=0;
            for(int i=0;i<n;i++) {
                double sf=r.scatterFraction*sumF[i]/26,st=r.scatterFraction*sumT[i]/26;
                difference+=Math.abs(sf-scatterF[i])+Math.abs(st-scatterT[i]);
                normScatter+=sf+st;scatterF[i]=sf;scatterT[i]=st;
            }
            double relativeChange=normScatter==0?0:difference/normScatter;
            double signalError=Math.abs(emitted-absorbed-escaped)/emitted;
            if(relativeChange<=r.relativeTolerance&&signalError<=r.relativeTolerance*10){converged=true;break;}
        }
        if(!converged)throw new ArithmeticException("Directional transport did not converge");
        ThermalGraph graph=new ThermalGraph(g,r);
        Solution solution=solve(graph,heat,r);
        double peakLoad=0,peakEscape=0;int hot=-1,leak=-1,face=-1;
        for(int i=0;i<n;i++) {
            if(solution.values[i]>peakLoad){peakLoad=solution.values[i];hot=i;}
            for(int f=0;f<6;f++)if(escapeFaces[6*i+f]>peakEscape){peakEscape=escapeFaces[6*i+f];leak=i;face=f;}
        }
        return new Raw(heat,solution.values,escapeFaces,emitted,absorbed,escaped,peakLoad,peakEscape,hot,leak,face,transportIterations,solution.iterations,solution.residual,graph.from.length);
    }

    /** Sparse, symmetric positive definite graph operator with exterior sinks. */
    static final class ThermalGraph {
        final int[] from,to;
        final double[] conductance,diagonal,sink;
        ThermalGraph(Grid g,Rules r) {
            int n=g.size;
            long count=((3L*g.nx-2)*(3L*g.ny-2)*(3L*g.nz-2)-n)/2;
            if(count>Integer.MAX_VALUE)throw new IllegalArgumentException("Graph too large for Java arrays");
            from=new int[(int)count];to=new int[(int)count];conductance=new double[(int)count];
            diagonal=new double[n];sink=new double[n];double[] c=new double[n];
            for(int i=0;i<n;i++)c[i]=r.conductionFloor+g.cells[i].c;
            int edge=0;
            for(int z=0;z<g.nz;z++)for(int y=0;y<g.ny;y++)for(int x=0;x<g.nx;x++) {
                int i=x+g.nx*(y+g.ny*z);
                int faces=(x==0?1:0)+(x==g.nx-1?1:0)+(y==0?1:0)+(y==g.ny-1?1:0)+(z==0?1:0)+(z==g.nz-1?1:0);
                sink[i]=r.surfaceCooling*c[i]*faces;diagonal[i]+=sink[i];
                for(Direction d:DIRECTIONS) {
                    int xx=x+d.x,yy=y+d.y,zz=z+d.z;if(!g.inside(xx,yy,zz))continue;
                    int j=xx+g.nx*(yy+g.ny*zz);if(j<=i)continue;
                    double k=2*c[i]*c[j]/(c[i]+c[j])/d.length;
                    from[edge]=i;to[edge]=j;conductance[edge]=k;edge++;
                    diagonal[i]+=k;diagonal[j]+=k;
                }
            }
            if(edge!=count)throw new AssertionError("Incorrect edge count");
        }
        void multiply(double[] x,double[] out) {
            for(int i=0;i<x.length;i++)out[i]=sink[i]*x[i];
            for(int e=0;e<from.length;e++) {
                int i=from[e],j=to[e];double v=conductance[e]*(x[i]-x[j]);
                out[i]+=v;out[j]-=v;
            }
        }
    }
    static record Solution(double[] values,int iterations,double residual) {}
    static Solution solve(ThermalGraph a,double[] b,Rules rules) {
        int n=b.length;double[] x=new double[n],r=b.clone(),z=new double[n],p=new double[n],ap=new double[n];
        double norm=dot(b,b);
        if(norm==0)return new Solution(x,0,0);
        for(int i=0;i<n;i++){z[i]=r[i]/a.diagonal[i];p[i]=z[i];}
        double rz=dot(r,z),res=1;int iter=0;
        for(;iter<rules.maxIterations;iter++) {
            a.multiply(p,ap);double pap=dot(p,ap);
            if(!positive(pap))throw new ArithmeticException("Thermal solver lost positive definiteness");
            double alpha=rz/pap;
            for(int i=0;i<n;i++){x[i]+=alpha*p[i];r[i]-=alpha*ap[i];}
            res=Math.sqrt(dot(r,r)/norm);
            if(res<=rules.relativeTolerance) {iter++;break;}
            for(int i=0;i<n;i++)z[i]=r[i]/a.diagonal[i];
            double next=dot(r,z),beta=next/rz;rz=next;
            for(int i=0;i<n;i++)p[i]=z[i]+beta*p[i];
        }
        a.multiply(x,ap);double actual=0;
        for(int i=0;i<n;i++){double delta=ap[i]-b[i];actual+=delta*delta;}
        res=Math.sqrt(actual/norm);
        if(!Double.isFinite(res)||res>rules.relativeTolerance*10)
            throw new ArithmeticException("Thermal solver did not converge; relative residual="+res);
        return new Solution(x,iter,res);
    }
    static double dot(double[] a,double[] b) {double out=0;for(int i=0;i<a.length;i++)out+=a[i]*b[i];return out;}
    static boolean positive(double v) {return Double.isFinite(v)&&v>0;}

    public static void main(String[] args) {
        MatrixCoefficients model=balancedDefault();
        Grid g=referenceLayout();
        if(args.length==1&&args[0].equals("15")) {
            g=new Grid(15,15,15,Material.GRAPHITE);
            for(int z=0;z<15;z++)for(int y=0;y<15;y++)for(int x=0;x<15;x++) {
                if(x==0||x==14||y==0||y==14||z==0||z==14)g.set(x,y,z,Material.WATER);
                else if(x%3==1&&y%3==1&&z%3==1)g.set(x,y,z,Material.FUEL);
            }
        }
        Coefficients out=model.evaluate(g);
        System.out.printf(Locale.ROOT,"{\"heat\":%.10f,\"peakLeak\":%.10f}%n",out.heat,out.peakLeak);
    }
}
