package org.starexec.exceptions;

import java.util.List;

import org.starexec.data.database.Benchmarks;
import org.starexec.data.to.Benchmark;
import org.starexec.exceptions.StarExecException;

public class BenchmarkDependencyMissingException extends StarExecException {
    public BenchmarkDependencyMissingException(String message, Throwable cause) {
        super(message, cause);
    }

    public BenchmarkDependencyMissingException(String message) {
        super(message);
    }
    
    private static String getMissingDepsMessage(int benchId) {
        List<Benchmark> missingDependencies = Benchmarks.getBrokenBenchDependencies(benchId);
        String missingDeps = "";
        for(Benchmark bench : missingDependencies) {
            missingDeps = bench.getName() + ", ";
        }
        return "Missing dependencies: " + missingDeps;
    }
    
    public BenchmarkDependencyMissingException(int benchId) {
        super(getMissingDepsMessage(benchId));
    }
}
