package com.proficiosoftware.higlayout;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/*
 * HIGLayout.java - HIGLayout layout manager
 * Copyright (C) 1999 Daniel Michalik
 * Copyright (C) 2014 Jaap Geurts
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

/**
 * Layout manager based on idea of design grid. For description please see
 * tutorial included in download bundle.
 * 
 * @see cz.autel.dmi.HIGConstraints
 * @version 1.1 16/SEP/2014
 * @author Daniel Michalik (dmi@autel.cz), Romano Caserta (caserta@disy.net),
 *         Frank Behrens (frank@pinky.sax.de), Sven Behrens (behrens@disy.net)
 *         Alberto Ricart (aricart@smartsoft.com), Peter Reilly
 *         (Peter.Reilly@marconi.com), Jaap Geurts (jaapg@gmx.net)
 */
public class HIGLayout extends ViewGroup
{

  private static final int WIDTH_ZERO = 0;
  private static final int HEIGHT_ZERO = 0;
  private static final String LOGTAG = "HIGLayout";

  private int[] mColWidths;
  private int[] mRowHeights;
  private int mColCount;
  private int mRowCount;

  private int[] mWidenWeights;
  private int[] mHeightenWeights;

  private boolean mShowGrid = false;
  //
  private int[] mComputedWidths;
  private int[] mComputedHeights;

  private int mWidenWeightsSum = 0;
  private int mHeightenWeightsSum = 0;

  public HIGLayout(Context context)
  {
    super(context);
  }

  public HIGLayout(Context context, AttributeSet attrs)
  {
    this(context, attrs, 0);
  }

  public HIGLayout(Context context, AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);

    setWillNotDraw(false);
    // Gets the styles from XML file

    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.HIGlayout,
        defStyleAttr, 0);

    Log.d(LOGTAG, "Showing ALL attributes:");
    final int K = attrs.getAttributeCount();
    for (int j = 0; j < K; j++)
    {
      String name = attrs.getAttributeName(j);

      int nr = attrs.getAttributeNameResource(j);
      int rv = attrs.getAttributeResourceValue(j, -1);
      Log.d(LOGTAG, "Name: " + name);
      Log.d(LOGTAG, "NameResource: " + nr);
      Log.d(LOGTAG, "ResourceValue: " + rv);

    }

    try
    {
      String colw = a.getString(R.styleable.HIGlayout_column_widths);
      if (colw == null)
        throw new IllegalArgumentException("Missing attribute: column_widths");
      mColWidths = stringToIntArray(colw);
    }
    catch (NumberFormatException nfe)
    {
      throw new IllegalArgumentException(
          "Illegal value in column_widths attribute.", nfe);
    }

    try
    {
      String rowh = a.getString(R.styleable.HIGlayout_row_heights);
      if (rowh == null)
        throw new IllegalArgumentException("Missing attribute: row_heights");
      mRowHeights = stringToIntArray(rowh);
    }
    catch (NumberFormatException nfe)
    {
      throw new IllegalArgumentException(
          "Illegal value in row_heights attribute.", nfe);
    }

    mColCount = mColWidths.length;
    mRowCount = mRowHeights.length;

    try
    {
      String colw = a.getString(R.styleable.HIGlayout_column_weights);
      if (colw == null)
        mWidenWeights = new int[mColWidths.length];
      else
        mWidenWeights = stringToIntArray(colw);
    }
    catch (NumberFormatException nfe)
    {
      throw new IllegalArgumentException(
          "Illegal value in column_weights attribute.", nfe);
    }

    try
    {
      String roww = a.getString(R.styleable.HIGlayout_row_weights);
      if (roww == null)
        mHeightenWeights = new int[mRowHeights.length];
      else
        mHeightenWeights = stringToIntArray(roww);
    }
    catch (NumberFormatException nfe)
    {
      throw new IllegalArgumentException(
          "Illegal value in row_weights attribute.", nfe);
    }

    mShowGrid = a.getBoolean(R.styleable.HIGlayout_show_grid, false);
    if (mShowGrid)
      Log.i(LOGTAG, "Grid visualization enabled");
    else
      Log.i(LOGTAG, "Grid visualization disabled");

    if (mHeightenWeights.length != mRowCount)
      throw new IllegalArgumentException(
          "Weights list must match number of rows");
    if (mWidenWeights.length != mColCount)
      throw new IllegalArgumentException(
          "Weights list must match number of columns");

    mWidenWeightsSum = 0;
    for (int i = 0; i < mColCount; i++)
      mWidenWeightsSum += mWidenWeights[i];

    mHeightenWeightsSum = 0;
    for (int i = 0; i < mRowCount; i++)
      mHeightenWeightsSum += mHeightenWeights[i];

    mComputedWidths = new int[mColCount + 1];
    mComputedHeights = new int[mRowCount + 1];

    a.recycle();
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b)
  {
    Log.d(LOGTAG, "onLayout(booleam,int,int,int,int)");
    final int count = getChildCount();

    // get the available size of child view
    int sizeWidth = this.getMeasuredWidth();
    sizeWidth -= this.getPaddingLeft() + this.getPaddingRight();
    int sizeHeight = this.getMeasuredHeight();
    sizeHeight -= this.getPaddingTop() + this.getPaddingBottom();

    if (changed)
    {
      // Invalidate the cache. getColumnsX() and getRowsY() will recalc the
      // cache
      mComputedWidths = null;
      mComputedHeights = null;
    }
    // TODO: check what this does.
    int x[] = getColumnsX(sizeWidth, this);
    int y[] = getRowsY(sizeHeight, this);

    for (int i = 0; i < count; i++)
    {
      // Get the View to position
      View child = getChildAt(i);

      // Get the Views specific properties (like position and anchoring)
      LayoutParams c = (LayoutParams)child.getLayoutParams();
      /* first we centre component into cell */

      int width = child.getMeasuredWidth();
      int height = child.getMeasuredHeight();
      int cellw;
      int cellh;
      if (c.w < 0) // view fixed width in cell.
      {
        width = -c.w;
        cellw = x[c.x + 1] - x[c.x];
      }
      else
      {
        // TODO: consider adding corrections back
        // width += c.wCorrection;
        cellw = x[c.x + c.w] - x[c.x];
      }
      if (c.h < 0)
      {
        height = -c.h;
        cellh = y[c.y + 1] - y[c.y];
      }
      else
      {
        // TODO: consider adding corrections back
        // height += c.hCorrection;
        cellh = y[c.y + c.h] - y[c.y];
      }

      boolean allowXSize = true;
      boolean allowYSize = true;

      float dw = ((float)(cellw - width)) / 2.0f;
      float dh = ((float)(cellh - height)) / 2.0f;
      float compx = (float)x[c.x] + dw;
      float compy = (float)y[c.y] + dh;

      /* now anchor to cell borders */
      String anchor = c.anchor;
      boolean xSize = false; /* first move, then change width (when opposite border) */
      boolean ySize = false;
      if (anchor != null)
      {
        for (int j = anchor.length() - 1; j >= 0; j--)
        {
          if (anchor.charAt(j) == 'l')
          {
            compx = (float)x[c.x];
            if (xSize && allowXSize)
              width = cellw;
            xSize = true;
          }
          else if (anchor.charAt(j) == 'r')
          {
            if (xSize && allowXSize)
              width = cellw;
            else
              compx += dw;
            xSize = true;
          }
          else if (anchor.charAt(j) == 't')
          {
            compy = (float)y[c.y];
            if (ySize && allowYSize)
              height = cellh;
            ySize = true;
          }
          else if (anchor.charAt(j) == 'b')
          {
            if (ySize && allowYSize)
              height = cellh;
            else
              compy += dh;
            ySize = true;
          }
          else
          {
            throw new RuntimeException("Wrong character in anchor.");
          }
        }
      }

      // TODO: consider adding corrections back
      // child.setBounds((int)compx + c.xCorrection, (int)compy + c.yCorrection,
      // width, height);
      // child.layout(l,t,r,b);
      child.layout((int)compx, (int)compy, (int)(compx + width),
          (int)(compy + height));
    }

  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    Log.d(LOGTAG, "onMeasure(): " + getMeasuredWidth() + "x" + getMeasuredHeight());
    mComputedWidths = calcWidths();
    mComputedHeights = calcHeights();
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas)
  {
    super.onDraw(canvas);
    // super.dispatchDraw(canvas);
    Log.d(LOGTAG, "onDraw(Canvas)");
    if (!mShowGrid)
      return;

    int height = this.getHeight();
    int width = this.getWidth();
    Paint paint = new Paint();
    paint.setColor(Color.GREEN);
    paint.setStrokeWidth(0);
    paint.setStyle(Paint.Style.STROKE);

    for (int i = 0; i < mComputedWidths.length - 1; i++)
    {
      canvas.drawLine(mComputedWidths[i], 0, mComputedWidths[i], height, paint);
    }
    for (int i = 0; i < mComputedHeights.length - 1; i++)
    {
      canvas
          .drawLine(0, mComputedHeights[i], width, mComputedHeights[i], paint);
    }

  }

  @Override
  protected boolean checkLayoutParams(ViewGroup.LayoutParams p)
  {
    Log.d(LOGTAG, "checkLayoutParams(ViewGroup.LayoutParams)");
    return p instanceof LayoutParams;
  }

  @Override
  protected LayoutParams generateDefaultLayoutParams()
  {
    Log.d(LOGTAG, "generateDefaultLayoutParams()");
    return new LayoutParams();
  }

  @Override
  public LayoutParams generateLayoutParams(AttributeSet attrs)
  {
    Log.d(LOGTAG, "generateLayoutParams(AttributeSet)");
    return new LayoutParams(getContext(), attrs);
  }

  @Override
  protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p)
  {
    Log.d(LOGTAG, "generateLayoutParams(ViewGroup.LayoutParams)");
    return generateDefaultLayoutParams(); // TODO Change this?
  }

  /**
   * Converts a string of comma separated integer values to an integer array
   * 
   * @param resColWidths
   * @throws NumberFormatException
   * @return the converted array
   */
  private int[] stringToIntArray(String resColWidths)
  {
    String[] cw = resColWidths.split(",");
    int[] ia = new int[cw.length];
    for (int i = 0; i < cw.length; i++)
    {
      ia[i] = Integer.parseInt(cw[i]);
    }
    return ia;
  }

  public void setColumnWidthsHeights(int widths[], int heights[])
  {
    mColCount = widths.length;
    mRowCount = heights.length;

    mColWidths = new int[mColCount];
    System.arraycopy(widths, 0, mColWidths, 0, mColCount);
    mRowHeights = new int[mRowCount];
    System.arraycopy(heights, 0, mRowHeights, 0, mRowCount);

    mWidenWeights = new int[mColCount];
    mHeightenWeights = new int[mRowCount];

    mComputedWidths = new int[mColCount + 1];
    mComputedHeights = new int[mRowCount + 1];

  }

  /**
   *
   * @since 0.97
   */
  private Object reallocArray(Object src, int newSize)
  {
    Object dest = java.lang.reflect.Array.newInstance(src.getClass()
        .getComponentType(), newSize);
    System.arraycopy(src, 0, dest, 0, java.lang.reflect.Array.getLength(src));
    return dest;
  }

  /**
   * Sets column width, realloc arrays if there is need.
   * 
   * @since 0.97
   */
  public void setColumnWidth(int col, int width)
  {
    if (mColCount < col)
    {
      mColCount = col;
    }
    if (mColWidths.length <= col)
    {
      mColWidths = (int[])reallocArray(mColWidths, mColCount);
      mWidenWeights = (int[])reallocArray(mWidenWeights, mColCount);
      mComputedWidths = (int[])reallocArray(mComputedWidths, mColCount + 1);
    }
    mColWidths[col] = width;
  }

  /**
   * Sets row height, realloc arrays if there is need.
   * 
   * @since 0.97
   */
  public void setRowHeight(int row, int height)
  {
    if (mRowCount < row)
    {
      mRowCount = row;
    }
    if (mRowHeights.length <= row)
    {
      mRowHeights = (int[])reallocArray(mRowHeights, mRowCount);
      mHeightenWeights = (int[])reallocArray(mHeightenWeights, mRowCount);
      mComputedHeights = (int[])reallocArray(mComputedHeights, mRowCount + 1);
    }
    mRowHeights[row] = height;
  }

  /**
   * Sets weight of specified column. Weight determines distribution of
   * difference when resizing.
   * 
   * @param col
   *          index of column. Index must be > 0.
   */
  public void setColumnWeight(int col, int weight)
  {
    if (col >= mColCount)
    {
      throw new RuntimeException("Column index cannot be greater then "
          + mColCount + ".");
    }
    mWidenWeights[col] = weight;
    mWidenWeightsSum = 0;
    for (int i = 0; i < mColCount; i++)
      mWidenWeightsSum += mWidenWeights[i];
  }

  /**
   * Sets weight of specified row. Weight determines distribution of difference
   * when resizing.
   * 
   * @param row
   *          index of row. Index must be > 0.
   */
  public void setRowWeight(int row, int weight)
  {
    if (row >= mRowCount)
    {
      throw new RuntimeException("Column index cannot be greater then "
          + mRowCount + ".");
    }
    mHeightenWeights[row] = weight;
    mHeightenWeightsSum = 0;
    for (int i = 0; i < mRowCount; i++)
      mHeightenWeightsSum += mHeightenWeights[i];
  }

  private void solveCycles(int g[], int lengths[])
  {
    /* TODO: handle cycles of length 1*/
    int path[] = new int[g.length];
    int stackptr = 0;

    /* marks of visited vertices. 0 - not visited, 1 - visited, 2 - visited and set final value */
    byte visited[] = new byte[g.length];
    for (int i = g.length - 1; i >= 0; i--)
    {
      if ((g[i] < 0) && (visited[i] == 0))
      {
        int current = i;

        if (-g[i] > g.length - 1)
          throw new IllegalArgumentException(
              "Column or Row referencing non existing column or row");

        /* find cycle or path with cycle */
        stackptr = 0;
        int maxLength = 0;
        int last;
        do
        {
          maxLength = (lengths[current] > maxLength) ? lengths[current]
              : maxLength;
          path[stackptr++] = current;
          visited[current] = 1;
          last = current;
          current = -g[current];
        } while ((current > 0) && (visited[current] == 0));

        if (current <= 0)
        {
          /* there is no cycle, only end of path */
          maxLength = lengths[last];
        }
        else if (current == 0)
        {
          maxLength = lengths[last];
        }
        else if (visited[current] == 1)
        {
          /* cycle, max. cannot lie outside the cycle, find it */
          int start = current;
          maxLength = 0;
          do
          {
            maxLength = (lengths[current] > maxLength) ? lengths[current]
                : maxLength;
            current = -g[current];
          } while (start != current);
        }
        else if (visited[current] == 2)
        {
          /* this vertice already has final value */
          maxLength = lengths[current];
        }
        else
        {
          throw new RuntimeException("This should not happen.");
        }
        while (stackptr > 0)
        {
          lengths[path[--stackptr]] = maxLength;
          visited[path[stackptr]] = 2;
        }
      }
    }
  }

  private int[] calcWidths()
  {
    int[] widths = new int[mColCount + 1]; // add last one which is the last
                                           // border
    ArrayList<View>[] colComponents;

    colComponents = getViewsInColumns();
    for (int i = 0; i < mColCount; i++)
    {
      if (mColWidths[i] > 0) // use specified fixed width
      {
        widths[i] = mColWidths[i];
      }
      else
      // has not been calculated
      {
        int maxWidth = 0;
        ArrayList<View> iComps = colComponents[i];
        if (iComps != null)
        {
          for (int j = iComps.size() - 1; j >= 0; j--)
          {
            View c = iComps.get(j);
            // TODO: check if match_parent works
            c.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            Log.d(LOGTAG,
                "W:: " + c.getTag() + " wants to be: " + c.getMeasuredWidth()
                    + "x" + getMeasuredHeight());
            int width = c.getVisibility() != View.GONE ? c.getMeasuredWidth()
                : WIDTH_ZERO;
            maxWidth = (width > maxWidth) ? width : maxWidth;
          }
        }
        widths[i] = maxWidth;
      }
    }
    solveCycles(mColWidths, widths);

    widths[widths.length - 1] = getMeasuredWidth() - getPaddingRight();

    return widths;
  }

  private int[] calcHeights()
  {
    int[] heights = new int[mRowCount + 1]; // add one for the bottom row

    ArrayList<View>[] rowComponents;

    rowComponents = getViewsInRows();
    for (int i = 0; i < mRowCount; i++)
    {
      if (mRowHeights[i] > 0)// use specified fixed width
      {
        heights[i] = mRowHeights[i];
      }
      else
      // has not been calculated
      {
        ArrayList<View> iComps = rowComponents[i];
        int maxHeight = 0;
        if (iComps != null)
        {
          for (int j = iComps.size() - 1; j >= 0; j--)
          {
            View c = iComps.get(j);
            c.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            Log.d(LOGTAG,
                "H:: " + c.getTag() + " wants to be: " + c.getMeasuredWidth()
                    + "x" + getMeasuredHeight());

            int height = c.getVisibility() != View.GONE ? c.getMeasuredHeight()
                : HEIGHT_ZERO;
            maxHeight = (height > maxHeight) ? height : maxHeight;
          }
        }
        heights[i] = maxHeight;
      }
    }
    solveCycles(mRowHeights, heights);

    heights[heights.length - 1] = getMeasuredHeight() - getPaddingBottom();

    return heights;
  }

  @SuppressWarnings("unchecked")
  private ArrayList<View>[] getViewsInColumns()
  {
    ArrayList<?>[] list = new ArrayList<?>[mColCount];

    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++)
    {
      View view = getChildAt(i);
      LayoutParams params = (LayoutParams)view.getLayoutParams();

      ArrayList<View> subList = (ArrayList<View>)list[params.x];
      if (subList == null)
      {
        subList = new ArrayList<View>();
        list[params.x] = subList;
      }
      subList.add(view);

    }

    return (ArrayList<View>[])list;
  }

  @SuppressWarnings("unchecked")
  private ArrayList<View>[] getViewsInRows()
  {
    ArrayList<?>[] list = new ArrayList<?>[mRowCount];

    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++)
    {
      View view = getChildAt(i);
      LayoutParams params = (LayoutParams)view.getLayoutParams();
      ArrayList<View> subList = (ArrayList<View>)list[params.y];
      if (subList == null)
      {
        subList = new ArrayList<View>();
        list[params.y] = subList;
      }
      subList.add(view);
    }
    return (ArrayList<View>[])list;
  }

  private void distributeSizeDifference(int desiredLength, int[] lengths,
      int[] weights, int weightSum)
  {
    int preferred = 0;
    int newLength;
    for (int i = lengths.length - 2; i >= 0; i--)
      preferred += lengths[i];

    double unit = ((double)(desiredLength - preferred)) / (double)weightSum;

    for (int i = lengths.length - 2; i >= 0; i--)
    {
      newLength = lengths[i] + (int)(unit * (double)weights[i]);
      // TODO: perhaps implement minimum lengths
      // lengths[i] = (newLength > minLengths[i]) ? newLength : minLengths[i];
      lengths[i] = newLength;
    }
  }

  /**
   * returns array of x-coordinates of columns. First coordinate is stored in
   * x[0] Reference to this array is cached, so data should not be modified.
   */
  int[] getColumnsX(int targetWidth, View v)
  {
    if (mComputedWidths != null)
      return mComputedWidths;

    mComputedWidths = calcWidths();

    distributeSizeDifference(targetWidth, mComputedWidths, mWidenWeights,
        mWidenWeightsSum);

    return mComputedWidths;
  }

  /**
   * returns array of y-coordinates of rows. First coordinate is stored in y[0].
   * Reference to this array is cached, so data should not be modified.
   */
  int[] getRowsY(int targetHeight, View v)
  {
    if (mComputedHeights != null)
      return mComputedHeights;

    mComputedHeights = calcHeights();

    distributeSizeDifference(targetHeight, mComputedHeights, mHeightenWeights,
        mHeightenWeightsSum);

    return mComputedHeights;
  }

  public static class LayoutParams extends ViewGroup.MarginLayoutParams
  {
    public int x = 0;
    public int y = 0;
    public int w = 1;
    public int h = 1;
    public String anchor = "";

    public LayoutParams()
    {
      this(null, null);
    }

    public LayoutParams(Context c, AttributeSet attrs)
    {
      super(c, attrs);
      if (c == null && attrs == null)
        return;
      // TODO: get layout params such as position in cell
      TypedArray a = c.obtainStyledAttributes(attrs,
          R.styleable.HIGlayout_Layout);

      x = a.getInt(R.styleable.HIGlayout_Layout_layout_cellX, 0);
      y = a.getInt(R.styleable.HIGlayout_Layout_layout_cellY, -1);
      w = a.getInt(R.styleable.HIGlayout_Layout_layout_spanX, 1);
      h = a.getInt(R.styleable.HIGlayout_Layout_layout_spanY, 1);
      anchor = a.getString(R.styleable.HIGlayout_Layout_layout_anchor);

      a.recycle();
    }
  }

}
