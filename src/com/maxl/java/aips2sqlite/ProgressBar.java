/*
Copyright (c) 2013 Max Lungarella

This file is part of Aips2SQLite.

Aips2SQLite is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.maxl.java.aips2sqlite;

public class ProgressBar extends Thread {
	
	boolean show_progress = true;

	private String msg = "";

	public void init(String msg) {
		this.msg = msg;
	}
	
	@Override
	public void run() {
		String anim = "|/-\\";
		int x = 0;
		while (show_progress) {
			System.out.print("\r" + msg + anim.charAt(x++ % anim.length()));
			try {
				Thread.sleep(100);
			} catch (Exception e) {
				//
			}				
		}
	}
	
	public void stopp() {
		show_progress = false;
	}
}

