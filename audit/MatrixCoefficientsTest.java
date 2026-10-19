package com.gonzotech.machines.reactor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import static java.lang.Math.*;

public final class MatrixCoefficientsTest {
    static int checks;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    static void near(double a,double b,double tolerance,String text){check(Double.isFinite(a)&&Double.isFinite(b)&&abs(a-b)<=tolerance*max(1,max(abs(a),abs(b))),text+": "+a+" != "+b);}
    static MatrixCoefficients.Grid rotate(MatrixCoefficients.Grid g){
        var r=new MatrixCoefficients.Grid(g.ny,g.nx,g.nz,MatrixCoefficients.Material.WATER);
        for(int z=0;z<g.nz;z++)for(int y=0;y<g.ny;y++)for(int x=0;x<g.nx;x++)r.set(g.ny-1-y,x,z,g.get(x,y,z));
        return r;
    }
    /** Independent, slower oracle: launch each source ray and walk it to the boundary. */
    static double[][] oracle(MatrixCoefficients.Grid g,MatrixCoefficients.Rules r){
        double[] heat=new double[g.size],escape=new double[g.size*6];
        for(int z0=0;z0<g.nz;z0++)for(int y0=0;y0<g.ny;y0++)for(int x0=0;x0<g.nx;x0++){
            if(g.get(x0,y0,z0)!=MatrixCoefficients.Material.FUEL)continue;
            for(var dir:MatrixCoefficients.DIRECTIONS){
                int x=x0,y=y0,z=z0;double f=r.sourcePerFuel()/26,t=0;
                while(g.inside(x,y,z)){
                    var m=g.get(x,y,z);int i=g.index(x,y,z);
                    double sf=f*pow(1-m.af,dir.length),st=t*pow(1-m.at,dir.length),conv=sf*(1-pow(1-m.m,dir.length));
                    f=sf-conv;t=st+conv;heat[i]+=dir.length*(r.fastHeatWeight()*f+t);
                    int xx=x+dir.x,yy=y+dir.y,zz=z+dir.z;
                    if(!g.inside(xx,yy,zz)){
                        int cnt=(xx<0||xx>=g.nx?1:0)+(yy<0||yy>=g.ny?1:0)+(zz<0||zz>=g.nz?1:0);
                        double v=(f+t)/cnt;
                        if(xx<0)escape[6*i]+=v;if(xx>=g.nx)escape[6*i+1]+=v;
                        if(yy<0)escape[6*i+2]+=v;if(yy>=g.ny)escape[6*i+3]+=v;
                        if(zz<0)escape[6*i+4]+=v;if(zz>=g.nz)escape[6*i+5]+=v;
                    }
                    x=xx;y=yy;z=zz;
                }
            }
        }
        return new double[][]{heat,escape};
    }
    /** Independent 26-direction Jacobi time stepping, without directional sweeps. */
    static double[][] mixedOracle(MatrixCoefficients.Grid g,MatrixCoefficients.Rules r){
        int n=g.size;double[][] f=new double[26][n],t=new double[26][n];
        for(int tick=0;tick<5000;tick++){
            double[][] uf=new double[26][n],ut=new double[26][n],nf=new double[26][n],nt=new double[26][n];
            double[] sf=new double[n],st=new double[n],heat=new double[n],escape=new double[n*6];
            for(int d=0;d<26;d++)for(int z=0;z<g.nz;z++)for(int y=0;y<g.ny;y++)for(int x=0;x<g.nx;x++){
                int i=g.index(x,y,z);var m=g.get(x,y,z);double length=MatrixCoefficients.DIRECTIONS[d].length;
                double a=(f[d][i]+(m==MatrixCoefficients.Material.FUEL?r.sourcePerFuel()/26:0))*pow(1-m.af,length);
                double b=t[d][i]*pow(1-m.at,length),converted=a*(1-pow(1-m.m,length));
                uf[d][i]=a-converted;ut[d][i]=b+converted;sf[i]+=uf[d][i];st[i]+=ut[d][i];
                heat[i]+=length*(r.fastHeatWeight()*uf[d][i]+ut[d][i]);
            }
            for(int d=0;d<26;d++)for(int z=0;z<g.nz;z++)for(int y=0;y<g.ny;y++)for(int x=0;x<g.nx;x++){
                int i=g.index(x,y,z);var direction=MatrixCoefficients.DIRECTIONS[d];
                double of=(1-r.scatterFraction())*uf[d][i]+r.scatterFraction()*sf[i]/26;
                double ot=(1-r.scatterFraction())*ut[d][i]+r.scatterFraction()*st[i]/26;
                int xx=x+direction.x,yy=y+direction.y,zz=z+direction.z;
                if(g.inside(xx,yy,zz)){int j=g.index(xx,yy,zz);nf[d][j]=of;nt[d][j]=ot;}
                else{
                    int count=(xx<0||xx>=g.nx?1:0)+(yy<0||yy>=g.ny?1:0)+(zz<0||zz>=g.nz?1:0);double v=(of+ot)/count;
                    if(xx<0)escape[6*i]+=v;if(xx>=g.nx)escape[6*i+1]+=v;
                    if(yy<0)escape[6*i+2]+=v;if(yy>=g.ny)escape[6*i+3]+=v;
                    if(zz<0)escape[6*i+4]+=v;if(zz>=g.nz)escape[6*i+5]+=v;
                }
            }
            double delta=0,scale=0;
            for(int d=0;d<26;d++)for(int i=0;i<n;i++){delta+=abs(nf[d][i]-f[d][i])+abs(nt[d][i]-t[d][i]);scale+=nf[d][i]+nt[d][i];}
            if(delta/max(1,scale)<1e-12)return new double[][]{heat,escape};
            f=nf;t=nt;
        }
        throw new AssertionError("Independent mixed oracle did not converge");
    }
    static void validate(MatrixCoefficients.Grid grid,MatrixCoefficients.Rules rules){
        var raw=MatrixCoefficients.analyze(grid,rules);
        near(raw.absorbed()+raw.escaped(),raw.emitted(),1e-9,"signal conservation");
        near(Arrays.stream(raw.escapeFaces()).sum(),raw.escaped(),1e-11,"exit face conservation");
        if(raw.emitted()==0)return;
        var graph=new MatrixCoefficients.ThermalGraph(grid,rules);
        near(MatrixCoefficients.dot(graph.sink,raw.load()),Arrays.stream(raw.heatProduction()).sum(),1e-8,"thermal balance");
        check(raw.residual()<1e-8,"solver residual");
        for(double value:raw.load())check(value>=-1e-9&&Double.isFinite(value),"nonnegative finite load");
    }
    public static void main(String[] args){
        var rules=MatrixCoefficients.Rules.defaults();var engine=MatrixCoefficients.balancedDefault();
        var straightRules=new MatrixCoefficients.Rules(1,.05,0,.05,1,2000,1e-10);
        check(MatrixCoefficients.DIRECTIONS.length==26,"26 directions");
        var ref=MatrixCoefficients.referenceLayout();near(engine.evaluate(ref).heat(),.7,1e-10,"fixed reference calibration");
        var dryThreeCube=new MatrixCoefficients.Grid(3,3,3,MatrixCoefficients.Material.FUEL);
        near(engine.evaluate(dryThreeCube).heat(),2.6971,5e-5,"dry 27-sheath heat coefficient");
        near(engine.evaluate(dryThreeCube).peakLeak(),.8812,5e-5,"dry 27-sheath leakage coefficient");
        check(new MatrixCoefficients.ThermalGraph(ref,rules).from.length==158,"3-cube has 158 edges");

        // Superposition: three loaded TVELs spread equally over two sheath positions.
        var weightedSheaths=new MatrixCoefficients.Grid(3,1,1,MatrixCoefficients.Material.WATER)
            .set(0,0,0,MatrixCoefficients.Material.FUEL).set(2,0,0,MatrixCoefficients.Material.FUEL)
            .setFuelStrength(0,0,0,1.5).setFuelStrength(2,0,0,1.5);
        var scaledRules=new MatrixCoefficients.Rules(1.5,.05,.15,.05,1,2000,1e-10);
        var ordinarySheaths=new MatrixCoefficients.Grid(3,1,1,MatrixCoefficients.Material.WATER)
            .set(0,0,0,MatrixCoefficients.Material.FUEL).set(2,0,0,MatrixCoefficients.Material.FUEL);
        var distributed=MatrixCoefficients.analyze(weightedSheaths,rules);
        var scaled=MatrixCoefficients.analyze(ordinarySheaths,scaledRules);
        validate(weightedSheaths,rules);
        near(distributed.emitted(),3,1e-12,"loaded rods conserve total source under superposition");
        near(distributed.peakLoad(),scaled.peakLoad(),1e-12,"superposition matches equal per-sheath source strength");
        near(distributed.peakEscape(),scaled.peakEscape(),1e-12,"superposition matches leakage strength");
        check(weightedSheaths.copy().fuelStrength(0,0,0)==1.5,"grid copy preserves fuel loading");
        var unloadedSheath=new MatrixCoefficients.Grid(1,1,1,MatrixCoefficients.Material.FUEL)
            .setFuelStrength(0,0,0,0);
        near(engine.evaluate(unloadedSheath).heat(),0,0,"empty sheath has no source heat");
        near(engine.evaluate(unloadedSheath).peakLeak(),0,0,"empty sheath has no source leak");
        boolean badFuelStrength=false;
        try{ordinarySheaths.setFuelStrength(1,0,0,1);}catch(IllegalStateException e){badFuelStrength=true;}
        check(badFuelStrength,"fuel strength only applies to sheath cells");
        badFuelStrength=false;
        try{ordinarySheaths.setFuelStrength(0,0,0,Double.NaN);}catch(IllegalArgumentException e){badFuelStrength=true;}
        check(badFuelStrength,"nonfinite fuel strength is rejected");
        var empty=new MatrixCoefficients.Grid(3,4,2,MatrixCoefficients.Material.GRAPHITE);
        near(engine.evaluate(empty).heat(),0,0,"no source heat");near(engine.evaluate(empty).peakLeak(),0,0,"no source leak");
        var single=new MatrixCoefficients.Grid(1,1,1,MatrixCoefficients.Material.FUEL);
        var raw=MatrixCoefficients.analyze(single,rules);
        near(raw.escaped(),1,1e-12,"single transparent source exits fully");
        for(double f:raw.escapeFaces())near(f,1./6,1e-12,"isotropic open source face");validate(single,rules);
        near(engine.evaluate(single).peakLeak(),.625,1e-12,"fixed leakage scale");

        Random rng=new Random(77);
        for(int trial=0;trial<16;trial++){
            var g=new MatrixCoefficients.Grid(1+rng.nextInt(5),1+rng.nextInt(5),1+rng.nextInt(5),MatrixCoefficients.Material.WATER);
            for(int z=0;z<g.nz;z++)for(int y=0;y<g.ny;y++)for(int x=0;x<g.nx;x++)g.set(x,y,z,MatrixCoefficients.Material.values()[rng.nextInt(5)]);
            g.set(0,0,0,MatrixCoefficients.Material.FUEL);
            double[][] expected=oracle(g,straightRules);var got=MatrixCoefficients.analyze(g,straightRules);
            for(int i=0;i<g.size;i++)near(got.heatProduction()[i],expected[0][i],1e-11,"sweep versus source-ray oracle");
            for(int i=0;i<6*g.size;i++)near(got.escapeFaces()[i],expected[1][i],1e-11,"face leakage versus oracle");
            var a=engine.evaluate(g);var b=engine.evaluate(rotate(g));
            near(a.heat(),b.heat(),1e-8,"rotation-invariant heat");near(a.peakLeak(),b.peakLeak(),1e-11,"rotation-invariant leakage");
            validate(g,rules);
        }
        var shieldB=new MatrixCoefficients.Grid(5,5,5,MatrixCoefficients.Material.BORON).set(2,2,2,MatrixCoefficients.Material.FUEL);
        var shieldBa=new MatrixCoefficients.Grid(5,5,5,MatrixCoefficients.Material.BARIUM).set(2,2,2,MatrixCoefficients.Material.FUEL);
        var shieldG=new MatrixCoefficients.Grid(5,5,5,MatrixCoefficients.Material.GRAPHITE).set(2,2,2,MatrixCoefficients.Material.FUEL);
        check(engine.evaluate(shieldB).peakLeak()<engine.evaluate(shieldBa).peakLeak(),"boron absorbs more than barium");
        check(engine.evaluate(shieldB).heat()<engine.evaluate(shieldG).heat(),"absorber not awarded lost-path heat");
        var doubled=new MatrixCoefficients.Rules(2,.05,.15,.05,1,2000,1e-10);
        var refRaw=MatrixCoefficients.analyze(ref,rules);var doubleRaw=MatrixCoefficients.analyze(ref,doubled);
        near(doubleRaw.peakLoad(),2*refRaw.peakLoad(),1e-9,"source linearity before normalization");
        near(doubleRaw.peakEscape(),2*refRaw.peakEscape(),1e-12,"source peak linearity");
        boolean failed=false;try{new MatrixCoefficients.Grid(0,3,3,MatrixCoefficients.Material.WATER);}catch(IllegalArgumentException e){failed=true;}check(failed,"invalid dimensions");
        failed=false;try{new MatrixCoefficients.Calibration(0,.1);}catch(IllegalArgumentException e){failed=true;}check(failed,"invalid calibration");
        failed=false;try{MatrixCoefficients.analyze(shieldG,new MatrixCoefficients.Rules(1,.05,.15,.05,1,1,1e-10));}catch(ArithmeticException e){failed=true;}check(failed,"nonconvergence is explicit");
        var offAxis=new MatrixCoefficients.Grid(7,7,7,MatrixCoefficients.Material.GRAPHITE).set(3,3,3,MatrixCoefficients.Material.FUEL);
        int off=offAxis.index(5,4,3);
        near(MatrixCoefficients.analyze(offAxis,straightRules).heatProduction()[off],0,0,"straight ray misses off-axis point");
        check(MatrixCoefficients.analyze(offAxis,rules).heatProduction()[off]>0,"scattering reaches off-axis point");
        var mixed=new MatrixCoefficients.Grid(3,4,2,MatrixCoefficients.Material.GRAPHITE);
        mixed.set(0,1,0,MatrixCoefficients.Material.FUEL).set(2,3,1,MatrixCoefficients.Material.FUEL);
        mixed.set(1,1,0,MatrixCoefficients.Material.BORON).set(1,2,1,MatrixCoefficients.Material.WATER).set(0,2,1,MatrixCoefficients.Material.BARIUM);
        double[][] expectedMixed=mixedOracle(mixed,rules);var actualMixed=MatrixCoefficients.analyze(mixed,rules);
        for(int i=0;i<mixed.size;i++)near(actualMixed.heatProduction()[i],expectedMixed[0][i],1e-9,"scattering sweep versus independent time stepping");
        for(int i=0;i<6*mixed.size;i++)near(actualMixed.escapeFaces()[i],expectedMixed[1][i],1e-9,"scattered leakage versus independent time stepping");

        var large=new MatrixCoefficients.Grid(15,15,15,MatrixCoefficients.Material.GRAPHITE);
        for(int z=0;z<15;z++)for(int y=0;y<15;y++)for(int x=0;x<15;x++){
            if(x==0||x==14||y==0||y==14||z==0||z==14)large.set(x,y,z,MatrixCoefficients.Material.WATER);
            else if(x%3==1&&y%3==1&&z%3==1)large.set(x,y,z,MatrixCoefficients.Material.FUEL);
        }
        validate(large,rules);check(new MatrixCoefficients.ThermalGraph(large,rules).from.length==38066,"15-cube sparse edge count");
        var largeRaw=MatrixCoefficients.analyze(large,rules);
        for(int i=0;i<4;i++)engine.evaluate(large);
        long start=System.nanoTime();MatrixCoefficients.Coefficients result=null;
        for(int i=0;i<10;i++)result=engine.evaluate(large);
        double ms=(System.nanoTime()-start)/1e6/10;
        System.out.printf(Locale.ROOT,"Reference: %s%n15^3: %s%n15^3 transport_iterations=%d thermal_iterations=%d relative_residual=%.3e mean_ms=%.3f%n",engine.evaluate(ref),result,largeRaw.transportIterations(),largeRaw.thermalIterations(),largeRaw.residual(),ms);
        System.out.printf(Locale.ROOT,"Boron shell: %s%nBarium shell: %s%nGraphite shell: %s%n",engine.evaluate(shieldB),engine.evaluate(shieldBa),engine.evaluate(shieldG));
        System.out.printf(Locale.ROOT,"Calibration heatBudget=%.12f leakHalfScale=%.12f%n",engine.calibration().heatBudget(),engine.calibration().leakHalfScale());
        ReactorOperatingMetricsTest.run();
        ReactorHeatDynamicsTest.run();
        System.out.println("PASS: "+checks+" assertions");
    }
}
