package org.starexec.data.to.compare;

import org.starexec.data.to.Solver;

import java.util.Comparator;

public class SolverComparator implements Comparator<Solver> {
	private final int column;
	private final boolean asc;

	public SolverComparator(int c, boolean a) {
		column = c;
		asc = a;
	}

	/**
	 * Compares solvers depending on the given column 0 name 1 description 2 id
	 */
	@Override
	public int compare(Solver o1, Solver o2) {
		if (!asc) {
			Solver temp = o1;
			o1 = o2;
			o2 = temp;
		}
		if (column == 1) {
			return o1.getDescription().compareToIgnoreCase(o2.getDescription());
		} else if (column == 2) {
			return Integer.compare(o1.getId(), o2.getId());
		}
		return o1.getName().compareToIgnoreCase(o2.getName());
	}
}
