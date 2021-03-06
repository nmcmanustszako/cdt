/*******************************************************************************
 * Copyright (c) 2012, 2013 Google, Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 	   Sergey Prigogin (Google) - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.refactoring.includes;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import com.ibm.icu.text.Collator;

public class IncludeInfo implements Comparable<IncludeInfo> {
	private static final Collator COLLATOR = Collator.getInstance();

	private final String name;
	private final boolean isSystem;

	public IncludeInfo(String name, boolean isSystem) {
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException();
		this.name = name;
		this.isSystem = isSystem;
	}

	public IncludeInfo(String includeText) {
		if (includeText == null || includeText.isEmpty())
			throw new IllegalArgumentException();
		boolean isSystem = false;
		int begin = 0;
		switch (includeText.charAt(0)) {
		case '<':
			isSystem = true;
			//$FALL-THROUGH$
		case '"':
			++begin;
			break;
		}
		int end = includeText.length();
		switch (includeText.charAt(end - 1)) {
		case '>':
		case '"':
			--end;
			break;
		}
		if (begin >= end)
			throw new IllegalArgumentException();

		this.name = includeText.substring(begin, end);
		this.isSystem = isSystem;
	}

	/**
	 * Returns the part of the include statement identifying the included header file without
	 * quotes or angle brackets.
	 */
	public final String getName() {
		return name;
	}

	public final boolean isSystem() {
		return isSystem;
	}

	@Override
	public int hashCode() {
		return name.hashCode() * 31 + (isSystem ? 1 : 0);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IncludeInfo other = (IncludeInfo) obj;
		return name.equals(other.name) && isSystem == other.isSystem;
	}

	/**
	 * Returns the include string as it appears in an {@code #include} statement.
	 */
	@Override
	public String toString() {
		return (isSystem ? '<' : '"') + name + (isSystem ? '>' : '"');
	}

	@Override
	public int compareTo(IncludeInfo other) {
		if (isSystem != other.isSystem) {
			return isSystem ? -1 : 1;
		}
		IPath path1 = Path.fromOSString(name);
		IPath path2 = Path.fromOSString(other.name);
		int length1 = path1.segmentCount();
		int length2 = path2.segmentCount();
		for (int i = 0; i < length1 && i < length2; i++) {
			int c = COLLATOR.compare(path1.segment(i), path2.segment(i));
			if (c != 0)
				return c;
		}
		return length1 - length2;
	}
}
