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
 * A dot is a matrix of 3D coordinates. The dot has all the methods to scale, rotate, translate
 * itself and also to project its coordinates into a 2D representation.
 * 
 * All transformations are *applied* to the dot (not saved in a transformation matrix) to optimize
 * further drawing of the dots. 
 * 
 */
package com.gfabre.android.threeD;

import java.util.Comparator;

public class Dot extends Matrix {
	static final double NO_TRANSFORM_VALUES[][]={{1, 0, 0},
												 {0, 1, 0}, 
												 {0, 0, 1}};
	public static final Matrix	noTransformMatrix = new Matrix(3, 3, NO_TRANSFORM_VALUES);
	
    static final int X = 0, 
					 Y = 1,
					 Z = 2,
					 NUM_DIMENSIONS = 3; 

	private double	xProjection, yProjection; 		// projected coordinates

	public Dot() {
		// for serialization purpose...
		super(1, NUM_DIMENSIONS);
	}

	/**
	 * 
	 * @param x is the x coordinate of the Mass
	 * @param y is the y coordinate of the Mass
	 * @param z is the z coordinate of the Mass
	 */
	public Dot(double x, double y, double z) {
		super(1, NUM_DIMENSIONS);
		values[0][X] = x;
		values[0][Y] = y;
		values[0][Z] = z;
	}
	
	public Dot(Matrix m) {
		super(1, NUM_DIMENSIONS);
		values[0][X] = m.getValues()[0][X];
		values[0][Y] = m.getValues()[0][Y];
		values[0][Z] = m.getValues()[0][Z];
	}

	/**
	 * 
	 * @return the x position of this
	 */
	public double getX() {
		return values[0][X];
	}

	public void  setX(double x)	{
		values[0][X] = x;
	}	
	
	/**
	 * 
	 * @return the y position of this
	 */
	public double getY() {
		return values[0][Y];
	}

	public void  setY(double y)	{
		values[0][Y] = y;
	}	
	
	/**
	 * 
	 * @return the z position of this
	 */
	public double getZ() {
		return values[0][Z];
	}
	
	public void  setZ(double z)	{
		values[0][Z] = z;
	}	
	
	/**
	 * 
	 * @return the x position of the Mass's z projection in the 2D dimension 
	 */
	public double getZX() {
		return xProjection;
	}

	/**
	 * 
	 * @return the y position of the Mass's z projection in the 2D dimension 
	 */
	public double getZY() {
		return yProjection;
	}
	
	/**
	 * Rotates this around the x, y, z axis 
	 * 
	 * @param Rx: rotation around the x axis in degrees
	 * @param Ry: rotation around the y axis in degrees
	 * @param Rz: rotation around the z axis in degrees
	 * 
	 * @return this rotated by Rx, Ry, Rz degrees.
	 */
	public Matrix	rotate(int Rx, int Ry, int Rz) {
		Matrix rMatrix = new Matrix(noTransformMatrix);
		rMatrix.rotate(Rx, Ry, Rz);
		
		return (Dot)this.mul(rMatrix);
	}

	/**
	 * Translates this by Dx, Dy, Dz units 
	 * 
	 * @param Dx: translation on the x axis
	 * @param Dy: translation on the y axis
	 * @param Dz: translation on the z axis
	 * 
	 * @return this translated by Dx, Dy, Dz units
	 */
	public Matrix translate(int Dx, int Dy, int Dz) {
		Dot delta = new Dot(Dx, Dy, Dz);
		
		return this.add(delta);
	}

	/**
	 * Scales this by Sx, Sy, Sz units 
	 * 
	 * @param Sx: scaling on the x axis
	 * @param Sy: scaling on the y axis
	 * @param Sz: scaling on the z axis
	 * 
	 * @return this scaled by Sx, Sy, Sz units
	 */
	public Matrix	scale(double Sx, double Sy, double Sz) {
		Matrix sMatrix = new Matrix(noTransformMatrix);
	  	sMatrix.scale(Sx, Sy, Sz);
	
		return this.mul(sMatrix);
	}
	
	/**
	 * 
	 * @param Xo defines the distance between the eye and the center of the screen
	 * @param Yo defines the distance between the eye and the center of the screen
	 * @param Zo defines the distance between the eye and the center of the screen
	 * @return this (now projected)
	 */
	public Dot project(double Xo, double Yo, double Zo) {
		if (Zo == 0) {
			// special projections from top/bottom or left/right
			if (Yo == 0) {
				// from left or right...
				isoProject(Xo);
				xProjection = getX();
			}
			if (Xo == 0) {
				// from top or bottom...
				isoProject(Yo);
				yProjection = getY();
			}
		}
			
		xProjection = Xo + Zo * (getX()-Xo) / (Zo-getZ());
		yProjection = Yo + Zo * (getY()-Yo) / (Zo-getZ());
		
		return this;
	}

	/**
	 * Assumes the eye is on the z axis (Xo and Yo are 0)
	 * 
	 * @param Zo defines the distance between the eye and the center of the screen
	 * @return this (now projected)
	 */
	public Dot isoProject(double Zo) {
		xProjection = Zo * getX() / (Zo-getZ());
		yProjection = Zo * getY() / (Zo-getZ());
		
		return this;
	}
	
	/**
	 * Computes the projected (as it appears once projected) distance between
	 * this' and mass' positions
	 * 
	 * @param dot the mass to compute the distance to
	 * @return the projected distance
	 */
	public double projectedDistance(Dot dot) {
		double s1, s2;
		
		s1 = getZY() - dot.getZY();
		s1 *= s1;
		s2 = getZX() - dot.getZX();
		s2 *= s2;
		return Math.sqrt(s1 + s2);
	}

	/**
	 * Computes the distance between this' and mass' positions
	 * 
	 * @param dot the mass to compute the distance to
	 * @return the distance
	 */
	public double distance(Dot dot) {
		double s1, s2, s3;
	
		if (dot == null)
			return 0.0;
		
		s1 = getX() - dot.getX();
		s1 *= s1;
		s2 = getY() - dot.getY();
		s2 *= s2;
		s3 = getZ() - dot.getZ();
		s3 *= s3;
		return Math.sqrt(s1 + s2 + s3);
	}
	
	public static Comparator<Dot> CompareOnX = new Comparator<Dot>() {
		public int compare(Dot dot1, Dot dot2) {
			if (dot1.getX() == dot2.getX())
				return 0;
			if (dot1.getX() < dot2.getX())
				return -1;
			else
				return 1;
		}
	};

	public static Comparator<Dot> CompareOnY = new Comparator<Dot>() {
		public int compare(Dot dot1, Dot dot2) {
			if (dot1.getY() == dot2.getY())
				return 0;
			if (dot1.getY() < dot2.getY())
				return 1;
			else
				return -1;
		}
	};

	public static Comparator<Dot> CompareOnZ = new Comparator<Dot>() {
		public int compare(Dot dot1, Dot dot2) {
			if (dot1.getZ() == dot2.getZ())
				return 0;
			if (dot1.getZ() < dot2.getZ())
				return 1;
			else
				return -1;
		}
	};
}
