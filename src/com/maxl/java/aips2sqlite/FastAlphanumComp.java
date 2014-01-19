package com.maxl.java.aips2sqlite;

import java.util.*;

/**
 * A <code>Comparator</code> for <code>CharSequence</code>s that
 * understands how to compare text strings containing numbers.  That
 * is, "z102" is considered to be greater than "z20."  This is
 * accomplished by dividing the compared strings into text and numeric
 * chunks.  Numeric chunks are compared numerically.<p>
 *
 * This Alphanum Algorithm is discussed at http://www.DaveKoelle.com
 *
 * @author Neil W. Weber (NeilWeber@yahoo.com)
 */
public class FastAlphanumComp<T extends CharSequence> implements Comparator<T>
{
	private static final byte TEXT = 1;
	private static final byte NUMERIC = 2;

	private boolean isNumeric(char ch) {
		return ch >= '0' && ch <= '9' || ch == '.';
	}

	public int compare(T s1, T s2) {
		int n1 = s1.length(), i1 = 0;
		int n2 = s2.length(), i2 = 0;

		while (i1 < n1 && i2 < n2) {
			// Find end of homogenous chunk in s1
			int e1 = i1;
			byte t1 = isNumeric(s1.charAt(i1)) ? NUMERIC : TEXT;
			while (e1 < n1) {
				if (isNumeric(s1.charAt(e1))) {
					if (t1 == NUMERIC)
						e1++;
					else
						break;
				}
				else if (t1 == TEXT)
					e1++;
				else
					break;
			}

			// System.out.println("Chunk #1: '" + s1.subSequence(i1, e1) + "'");

			// Find end of homogenous chunk in s2
			int e2 = i2;
			byte t2 = isNumeric(s2.charAt(i2)) ? NUMERIC : TEXT;
			while (e2 < n2) {
				if (isNumeric(s2.charAt(e2))) {
					if (t2 == NUMERIC)
						e2++;
					else
						break;
				}
				else if (t2 == TEXT)
					e2++;
				else
					break;
			}

			// System.out.println("Chunk #2: '" + s2.subSequence(i2, e2) + "'");

			// If both chunks contain numeric characters, sort them numerically
			if (t1 == NUMERIC && t2 == NUMERIC) {
				try {
					float f1 = Float.parseFloat(s1.subSequence(i1, e1 - 1).toString());
					float f2 = Float.parseFloat(s2.subSequence(i2, e2 - 1).toString());
					int result = Float.compare(f1, f2);
					// System.out.println(f1 + "  " + f2 + "  " + result);
					if (result != 0)
						return result;
					else {
						i1 = e1;
						i2 = e2;
						continue;
					}
				}
				catch (NumberFormatException ex)
				{
					// Fallback to textual comparison
				}
			}

			while (i1 < e1 && i2 < e2) {
				char c1 = s1.charAt(i1);
				char c2 = s2.charAt(i2);
				if (c1 != c2)
					return c1 - c2;

				i1++;
				i2++;
			}
		}

		return 0;
	}
}