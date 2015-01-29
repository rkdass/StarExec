package org.starexec.test.database;

import java.util.Random;

import org.junit.Assert;
import org.starexec.data.to.Benchmark;
import org.starexec.data.to.Configuration;
import org.starexec.data.to.JobPair;
import org.starexec.data.to.Processor;
import org.starexec.data.to.Solver;
import org.starexec.data.to.compare.BenchmarkComparator;
import org.starexec.data.to.compare.JobPairComparator;
import org.starexec.data.to.compare.SolverComparator;
import org.starexec.data.to.compare.SolverComparisonComparator;
import org.starexec.test.Test;
import org.starexec.test.TestSequence;
import org.starexec.test.TestUtil;

//this class ensures that all of the comparators in org/starexec/data/to/compare are working properly
// by checking to see if they sort correctly on all their relevant columns

public class ComparatorTests extends TestSequence {
	Random rand=new Random();
	@Test
	private void benchmarkComparatorTest() {
		BenchmarkComparator comp=new BenchmarkComparator(0);
		
		for (int x=0;x<100;x++) {
			String a=TestUtil.getRandomAlphaString(rand.nextInt(10)+1);
			String b=TestUtil.getRandomAlphaString(rand.nextInt(10)+1);
			Benchmark b1=new Benchmark();
			Benchmark b2=new Benchmark();
			b1.setName(a);
			b2.setName(b);
			Assert.assertTrue(comp.compare(b1, b2)==a.compareToIgnoreCase(b));
		}
		
		comp=new BenchmarkComparator(1);
		
		for (int x=0;x<100;x++) {
			String a=TestUtil.getRandomAlphaString(rand.nextInt(10)+1);
			String b=TestUtil.getRandomAlphaString(rand.nextInt(10)+1);
			Benchmark b1=new Benchmark();
			Benchmark b2=new Benchmark();
			Processor p1=new Processor();
			Processor p2=new Processor();
			p1.setName(a);
			p2.setName(b);
			b1.setType(p1);
			b2.setType(p2);
			Assert.assertTrue(comp.compare(b1, b2)==a.compareToIgnoreCase(b));
		}
	}
	
	@Test
	private void solverComparatorTest() {
		SolverComparator comp=new SolverComparator(0);
		
		for (int x=0;x<100;x++) {
			String a=TestUtil.getRandomAlphaString(rand.nextInt(10)+1);
			String b=TestUtil.getRandomAlphaString(rand.nextInt(10)+1);
			
			Solver s1=new Solver();
			Solver s2=new Solver();
			s1.setName(a);
			s2.setName(b);
			Assert.assertTrue(comp.compare(s1, s2)==a.compareToIgnoreCase(b));
		}
		
		comp=new SolverComparator(1);
		
		for (int x=0;x<100;x++) {
			String a=TestUtil.getRandomAlphaString(rand.nextInt(10)+1);
			String b=TestUtil.getRandomAlphaString(rand.nextInt(10)+1);
			
			Solver s1=new Solver();
			Solver s2=new Solver();
			s1.setDescription(a);
			s2.setDescription(b);
			Assert.assertTrue(comp.compare(s1, s2)==a.compareToIgnoreCase(b));
		}
		
		comp=new SolverComparator(2);
		
		for (int x=0;x<100;x++) {
			int a=rand.nextInt(100)+2;
			int b=rand.nextInt(100)+2;

			Solver s1=new Solver();
			Solver s2=new Solver();
			s1.setId(a);
			s2.setId(b);
			Assert.assertTrue(comp.compare(s1, s2)==Integer.valueOf(a).compareTo(b));

		}
	}
	
	@Test
	private void JobPairComparatorTest() {
		JobPairComparator comp=new JobPairComparator(0);
		for (int x=0;x<100;x++) {
			String a=TestUtil.getRandomAlphaString(rand.nextInt(10)+2);
			String b=TestUtil.getRandomAlphaString(rand.nextInt(10)+2);
			Benchmark b1=new Benchmark();
			Benchmark b2=new Benchmark();
			b1.setName(a);
			b2.setName(b);
			JobPair p1=new JobPair();
			JobPair p2=new JobPair();
			p1.setBench(b1);
			p2.setBench(b2);
			Assert.assertTrue(comp.compare(p1, p1)==a.compareToIgnoreCase(b));
		}
		comp=new JobPairComparator(1);
		for (int x=0;x<100;x++) {
			String a=TestUtil.getRandomAlphaString(rand.nextInt(10)+2);
			String b=TestUtil.getRandomAlphaString(rand.nextInt(10)+2);
			Solver s1=new Solver();
			Solver s2=new Solver();
			s1.setName(a);
			s2.setName(b);
			JobPair p1=new JobPair();
			JobPair p2=new JobPair();
			p1.setSolver(s1);
			p2.setSolver(s2);
			Assert.assertTrue(comp.compare(p1, p1)==a.compareToIgnoreCase(b));
		}
		comp=new JobPairComparator(2);
		for (int x=0;x<100;x++) {
			String a=TestUtil.getRandomAlphaString(rand.nextInt(10)+2);
			String b=TestUtil.getRandomAlphaString(rand.nextInt(10)+2);
			Configuration c1=new Configuration();
			Configuration c2=new Configuration();
			c1.setName(a);
			c2.setName(b);
			JobPair p1=new JobPair();
			JobPair p2=new JobPair();
			p1.setConfiguration(c1);
			p2.setConfiguration(c2);
			Assert.assertTrue(comp.compare(p1, p1)==a.compareToIgnoreCase(b));
		}
		
		comp=new JobPairComparator(4);
		for (int x=0;x<100;x++) {
			int a=rand.nextInt(100)+3;
			int b=rand.nextInt(100)+3;
			
			JobPair p1=new JobPair();
			JobPair p2=new JobPair();
			p1.setWallclockTime(a);
			p2.setWallclockTime(b);
			Assert.assertTrue(comp.compare(p1, p1)==Double.compare(a, b));
		}
		
		comp=new JobPairComparator(8);
		for (int x=0;x<100;x++) {
			int a=rand.nextInt(100)+3;
			int b=rand.nextInt(100)+3;
			
			JobPair p1=new JobPair();
			JobPair p2=new JobPair();
			p1.setCpuUsage(a);
			p2.setCpuUsage(b);
			Assert.assertTrue(comp.compare(p1, p1)==Double.compare(a, b));
		}
	}
	
	@Test
	private void SolverComparisionComparatorTest() {
		SolverComparisonComparator comp=new SolverComparisonComparator(0,false);
	}
	
	@Override
	protected String getTestName() {
		return "ComparatorTests";
	}

	@Override
	protected void setup() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void teardown() throws Exception {
		// TODO Auto-generated method stub
		
	}

}