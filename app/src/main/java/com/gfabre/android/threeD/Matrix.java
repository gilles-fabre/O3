/*
                   GNU LESSER GENERAL PUBLIC LICENSE
                       Version 3, 29 June 2007

 Copyright (C) 2007 Free Software Foundation, Inc. <http://fsf.org/>
 Everyone is permitted to copy and distribute verbatim copies
 of this license document, but changing it is not allowed.


  This version of the GNU Lesser General Public License incorporates
the terms and conditions of version 3 of the GNU General Public
License, supplemented by the additional permissions listed below.

  0. Additional Definitions.

  As used herein, "this License" refers to version 3 of the GNU Lesser
General Public License, and the "GNU GPL" refers to version 3 of the GNU
General Public License.

  "The Library" refers to a covered work governed by this License,
other than an Application or a Combined Work as defined below.

  An "Application" is any work that makes use of an interface provided
by the Library, but which is not otherwise based on the Library.
Defining a subclass of a class defined by the Library is deemed a mode
of using an interface provided by the Library.

  A "Combined Work" is a work produced by combining or linking an
Application with the Library.  The particular version of the Library
with which the Combined Work was made is also called the "Linked
Version".

  The "Minimal Corresponding Source" for a Combined Work means the
Corresponding Source for the Combined Work, excluding any source code
for portions of the Combined Work that, considered in isolation, are
based on the Application, and not on the Linked Version.

  The "Corresponding Application Code" for a Combined Work means the
object code and/or source code for the Application, including any data
and utility programs needed for reproducing the Combined Work from the
Application, but excluding the System Libraries of the Combined Work.

  1. Exception to Section 3 of the GNU GPL.

  You may convey a covered work under sections 3 and 4 of this License
without being bound by section 3 of the GNU GPL.

  2. Conveying Modified Versions.

  If you modify a copy of the Library, and, in your modifications, a
facility refers to a function or data to be supplied by an Application
that uses the facility (other than as an argument passed when the
facility is invoked), then you may convey a copy of the modified
version:

   a) under this License, provided that you make a good faith effort to
   ensure that, in the event an Application does not supply the
   function or data, the facility still operates, and performs
   whatever part of its purpose remains meaningful, or

   b) under the GNU GPL, with none of the additional permissions of
   this License applicable to that copy.

  3. Object Code Incorporating Material from Library Header Files.

  The object code form of an Application may incorporate material from
a header file that is part of the Library.  You may convey such object
code under terms of your choice, provided that, if the incorporated
material is not limited to numerical parameters, data structure
layouts and accessors, or small macros, inline functions and templates
(ten or fewer lines in length), you do both of the following:

   a) Give prominent notice with each copy of the object code that the
   Library is used in it and that the Library and its use are
   covered by this License.

   b) Accompany the object code with a copy of the GNU GPL and this license
   document.

  4. Combined Works.

  You may convey a Combined Work under terms of your choice that,
taken together, effectively do not restrict modification of the
portions of the Library contained in the Combined Work and reverse
engineering for debugging such modifications, if you also do each of
the following:

   a) Give prominent notice with each copy of the Combined Work that
   the Library is used in it and that the Library and its use are
   covered by this License.

   b) Accompany the Combined Work with a copy of the GNU GPL and this license
   document.

   c) For a Combined Work that displays copyright notices during
   execution, include the copyright notice for the Library among
   these notices, as well as a reference directing the user to the
   copies of the GNU GPL and this license document.

   d) Do one of the following:

       0) Convey the Minimal Corresponding Source under the terms of this
       License, and the Corresponding Application Code in a form
       suitable for, and under terms that permit, the user to
       recombine or relink the Application with a modified version of
       the Linked Version to produce a modified Combined Work, in the
       manner specified by section 6 of the GNU GPL for conveying
       Corresponding Source.

       1) Use a suitable shared library mechanism for linking with the
       Library.  A suitable mechanism is one that (a) uses at run time
       a copy of the Library already present on the user's computer
       system, and (b) will operate properly with a modified version
       of the Library that is interface-compatible with the Linked
       Version.

   e) Provide Installation Information, but only if you would otherwise
   be required to provide such information under section 6 of the
   GNU GPL, and only to the extent that such information is
   necessary to install and execute a modified version of the
   Combined Work produced by recombining or relinking the
   Application with a modified version of the Linked Version. (If
   you use option 4d0, the Installation Information must accompany
   the Minimal Corresponding Source and Corresponding Application
   Code. If you use option 4d1, you must provide the Installation
   Information in the manner specified by section 6 of the GNU GPL
   for conveying Corresponding Source.)

  5. Combined Libraries.

  You may place library facilities that are a work based on the
Library side by side in a single library together with other library
facilities that are not Applications and are not covered by this
License, and convey such a combined library under terms of your
choice, if you do both of the following:

   a) Accompany the combined library with a copy of the same work based
   on the Library, uncombined with any other library facilities,
   conveyed under the terms of this License.

   b) Give prominent notice with the combined library that part of it
   is a work based on the Library, and explaining where to find the
   accompanying uncombined form of the same work.

  6. Revised Versions of the GNU Lesser General Public License.

  The Free Software Foundation may publish revised and/or new versions
of the GNU Lesser General Public License from time to time. Such new
versions will be similar in spirit to the present version, but may
differ in detail to address new problems or concerns.

  Each version is given a distinguishing version number. If the
Library as you received it specifies that a certain numbered version
of the GNU Lesser General Public License "or any later version"
applies to it, you have the option of following the terms and
conditions either of that published version or of any later version
published by the Free Software Foundation. If the Library as you
received it does not specify a version number of the GNU Lesser
General Public License, you may choose any version of the GNU Lesser
General Public License ever published by the Free Software Foundation.

  If the Library as you received it specifies that a proxy can decide
whether future versions of the GNU Lesser General Public License shall
apply, that proxy's public statement of acceptance of any version is
permanent authorization for you to choose that version for the
Library.

Initial version by gilles fabre (gilles.fabre.perso@free.fr), March 2015
*/

/**
 * Base matrix handling for 3D: mul and add operations between square matrixes 
 * and vector/matrixes.
 *    
 */
package com.gfabre.android.threeD;

import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * A class to handle basic matrix operations for 3D.
 */
public class Matrix {
	protected int		columns,			// matrix size, non squared are handled, but operations generate squared (min(m1.rows, m2.rows), min(m1.columns, m2.columns)) matrixes
						lines;
	protected double 	[][]values;			// the actual double values
	
	/**
     * Saves its own fields by calling defaultWriteObject.
     * 
     */
    private void writeObject(ObjectOutputStream out)  throws IOException {
    	out.defaultWriteObject();
 	
    	for (int i = 0; i < lines; i++) {
 	    	for (int j = 0; j < columns; j++) {
 	    		out.writeObject(new Float(values[i][j]));
 	    	}
 	    }
  }

    public Matrix() {
		// for serialization purpose...
	}
    
	// empty matrix constructor
	public Matrix(int _lines, int _columns) {
		columns = _columns;
		lines = _lines;
		values = new double[lines][columns];
	}
	
	// initialized matrix constructor
	public Matrix(int _lines, int _columns, double [][]grid) {
		columns = _columns;
		lines = _lines;
		values = new double[lines][columns];
		for (int i = 0; i < lines; i++) {
			for (int j = 0; j < columns; j++) {
				values[i][j] = grid[i][j];
			}
		}
	}

	// copy constructor
	public Matrix(Matrix matrix) {
		columns = matrix.columns;
		lines = matrix.lines;
		values = new double[lines][columns];
		for (int i = 0; i < lines; i++)
			for (int j = 0; j < columns; j++)
				values[i][j] = matrix.values[i][j];
	}
	
	/**
	 * matrix multiplication
	 * 
	 * @param m: matrix to multiply this with
	 * @return this * m
	 */ 
	public Matrix mul(Matrix m) {
		double [][]_values;
		
		_values = new double[lines][columns];

		for (int i = 0; i < lines; i++) {
			for (int j = 0; j < columns; j++) {
				_values[i][j] = 0.0;
				for (int k = 0; k < columns && k < m.lines /* non square matrixes! */ ; k++) {
					_values[i][j] += values[i][k] * m.values[k][j];
				}
			}
		}
		
		values = _values;
		return this;
	}

	/**
	 * matrix multiplication by a double
	 * 
	 * @param d: double to multiply this with
	 * @return this * d
	 */ 
	public Matrix mul(double d) {
		for (int i = 0; i < lines; i++) {
			for (int j = 0; j < columns; j++) {
				values[i][j] *= d;
			}
		}
		
		return this;
	}

	/**
	 * matrix subtract
	 * 
	 * @param m: matrix to sub to this
	 * @return this - m
	 */ 
	public Matrix sub(Matrix m) {
		double [][]_values;
		
		_values = new double[lines][columns];

		for (int i = 0; i < lines; i++) {
			for (int j = 0; j < columns; j++) {
					_values[i][j] = values[i][j] - m.values[i][j];
			}
		}
		
		values = _values;
		return this;
	}

	/**
	 * matrix substract with a double
	 * 
	 * @param d: double to sub to this
	 * @return this - d
	 */ 
	public Matrix sub(double d) {
		for (int i = 0; i < lines; i++) {
			for (int j = 0; j < columns; j++) {
				values[i][j] -= d;
			}
		}
		
		return this;
	}

	/**
	 * matrix addition
	 * 
	 * @param m: matrix to add to this
	 * @return this + m
	 */ 
	public Matrix add(Matrix m) {
		double [][]_values;
		
		_values = new double[lines][columns];

		for (int i = 0; i < lines; i++) {
			for (int j = 0; j < columns; j++) {
					_values[i][j] = values[i][j] + m.values[i][j];
			}
		}
		
		values = _values;
		return this;
	}

	/**
	 * matrix addition with a double
	 * 
	 * @param d: double to add to this
	 * @return this + d
	 */ 
	public Matrix add(double d) {
		for (int i = 0; i < lines; i++) {
			for (int j = 0; j < columns; j++) {
				values[i][j] += d;
			}
		}
		
		return this;
	}

	/**
	 * matrix transposition
	 *
	 * @return this where values[i][j] was changed into valyes[j][i] for all i,j in lines,columns
	 */ 
	public Matrix transpose() {
		for (int i = 0; i < lines; i++) {
			for (int j = 0; j < columns; j++) {
				values[i][j] = values[j][i];
			}
		}
		
		return this;
	}
	
	/**
	 * matrix zeroing
	 *
	 * @return this with all entries reset to 0
	 */ 
	public Matrix zero() {
		return mul(0.0);
	}

	public Matrix populate(double [][]grid) {
		for (int i = 0; i < lines; i++) {
			for (int j = 0; j < columns; j++) {
				values[i][j] = grid[i][j];
			}
		}
		
		return this;
	}


	public Matrix id()
	{
		if (lines != columns)
			return null;
		
		for (int i=0; i < lines; i++) {
			for (int j=0; j < columns; j++) {
				if (i==j)
					values[i][j]=1;
				else
					values[i][j]=0;
			}
		}
		
		return this;
	}

	public double determinant()
	{
		if (lines != columns)
			return 0;
		
		if (lines == 2)
			return values[0][0] * values[1][1]-values[0][1] * values[1][0];
		
		if (lines == 1)
			return values[0][0];
		
		Matrix 	minorIJ;
		double	detIJ;
		double 	determinant = 0;
		int sign=1;
		for (int j=0; j < columns; j++)	{
			minorIJ = minor(0, j);
			detIJ = minorIJ.determinant();
			determinant += sign * detIJ * values[0][j];
			sign = -sign;
		}
		
		return determinant;
	}

	public Matrix cofactors()
	{
		if (lines != columns)
			return null;
		
		if (lines == 1)
			return this;
		
		Matrix minorIJ;
		double detIJ;
		Matrix mResult=new Matrix(lines, columns);
		for (int i=0; i < lines; i++) {
			for (int j=0; j < columns; j++)
			{
				minorIJ = minor(i,j);
				detIJ = minorIJ.determinant();
				mResult.values[i][j]= detIJ * Math.pow(-1,i+j);
			}
		}
		return mResult;
	}

	public Matrix inverse()
	{
		double determinant = this.determinant();
		Matrix mResult = this.cofactors();
		if (mResult != null) {
			mResult = mResult.transpose();
			mResult = mResult.mul(1/determinant);
		}
		
		return mResult;
	}
	

	public Matrix minor(int iM, int jM)
	{
		if (lines != columns || columns < 2)
			return null;
		
		int i,j;
		Matrix mResult = new Matrix(lines -1, columns-1);
		for (i=0; i < iM; i++) {
			for (j=0; j < jM; j++) {
				mResult.values[i][j]= values[i][j];
			}
			for (j=jM+1; j < columns; j++) {
				mResult.values[i][j-1]= values[i][j];
			}
		}
		for (i=iM+1; i < lines; i++) {
			for (j=0; j < jM; j++) {
				mResult.values[i-1][j]= values[i][j];
			}
			for (j=jM+1; j < columns; j++)	{
				mResult.values[i-1][j-1]= values[i][j];
			}
		}
		return mResult;
	}
	
	public Matrix	rotate(int Rx, int Ry, int Rz) {
		double [][]rGrid = new double[lines][columns];

		// build the rotation matrix
		rGrid[0][0] = TrigoTable.cos(Rz) * TrigoTable.cos(Ry); 
		rGrid[1][0] = TrigoTable.sin(Rz) * TrigoTable.cos(Ry); 
		rGrid[2][0] = -TrigoTable.sin(Ry);

		rGrid[0][1] = TrigoTable.cos(Rz) * TrigoTable.sin(Ry) * TrigoTable.sin(Rx) - TrigoTable.sin(Rz) * TrigoTable.cos(Rx); 
		rGrid[1][1] = TrigoTable.sin(Rz) * TrigoTable.sin(Ry) * TrigoTable.sin(Rx) + TrigoTable.cos(Rx) * TrigoTable.cos(Rz); 
		rGrid[2][1] = TrigoTable.sin(Rx) * TrigoTable.cos(Ry);

		rGrid[0][2] = TrigoTable.cos(Rz) * TrigoTable.sin(Ry) * TrigoTable.cos(Rx) + TrigoTable.sin(Rz) * TrigoTable.sin(Rx); 
		rGrid[1][2] = TrigoTable.sin(Rz) * TrigoTable.sin(Ry) * TrigoTable.cos(Rx) - TrigoTable.cos(Rz) * TrigoTable.sin(Rx); 
		rGrid[2][2] = TrigoTable.cos(Rx) * TrigoTable.cos(Ry);

		return this.mul(new Matrix(lines, columns, rGrid));
	}

	public Matrix scale(double Sx, double Sy, double Sz) {
		double [][]sGrid = new double[lines][columns];
		
	  	// build the scaling matrix
		sGrid[0][0] = Sx;
		sGrid[1][0] = 0.0;
		sGrid[2][0] = 0.0; 

		sGrid[0][1] = 0.0; 
		sGrid[1][1] = Sy; 
		sGrid[2][1] = 0.0;

		sGrid[0][2] = 0.0; 
		sGrid[1][2] = 0.0; 
		sGrid[2][2] = Sz;

	  	return this.mul(new Matrix(lines, columns, sGrid));
	}
	
	public double[][] getValues() {
		return values;
	}
}
