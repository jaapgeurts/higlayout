package com.proficiosoftware.higlayout;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.TypedArray;
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
  //
  private int[] mComputedWidths;
  private int[] mComputedHeights;

  private int mWidenWeightsSum = 0;
  private int mHeightenWeightsSum = 0;

  /* Following variables are for caching of computations: */
  private int mCacheColumnsX[];
  private int mCacheRowsY[];

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

    // DEBUG: dump attributes
    final int N = a.getIndexCount();
    Log.d(LOGTAG, "Showing FILTERED attributes:");
    for (int i = 0; i < N; i++)
    {
      int attr = a.getIndex(i);
      switch(attr)
      {
        case R.styleable.HIGlayout_column_weights:
          Log.d(LOGTAG, "HIGlayout_column_weights");
          break;
        case R.styleable.HIGlayout_row_heights:
          Log.d(LOGTAG, "HIGlayout_row_heights");
          break;
        case R.styleable.HIGlayout_column_widths:
          Log.d(LOGTAG, "HIGlayout_column_widths");
          break;
        case R.styleable.HIGlayout_row_weights:
          Log.d(LOGTAG, "HIGlayout_row_weights");
          break;
        default:
          Log.d(LOGTAG, "Spurious attribute: " + attr);
      }
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

    mComputedWidths = new int[mColCount];
    mComputedHeights = new int[mRowCount];

    a.recycle();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    Log.d(LOGTAG, "onMeasure()");
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
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

    mComputedWidths = new int[mColCount];
    mComputedHeights = new int[mRowCount];

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
      mCacheColumnsX = null;
      mCacheRowsY = null;
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
      mColWidths = (int[])reallocArray(mColWidths, mColCount + 3);
      mWidenWeights = (int[])reallocArray(mWidenWeights, mColCount + 3);
      mComputedWidths = (int[])reallocArray(mComputedWidths, mColCount + 3);
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
      mRowHeights = (int[])reallocArray(mRowHeights, mRowCount + 3);
      mHeightenWeights = (int[])reallocArray(mHeightenWeights, mRowCount + 3);
      mComputedHeights = (int[])reallocArray(mComputedHeights, mRowCount + 3);
    }
    mRowHeights[row] = height;
  }

  /**
   * Sets preferred width of specified column.
   * 
   * @param col
   *          index of column. Index must be > 0.
   * @param width
   *          the width to use in pixels
   * @since 1.0
   */
  public void setPreferredColumnWidth(int col, int width)
  {
    if (col >= mColCount)
    {
      throw new IllegalArgumentException("Column index cannot be greater then "
          + mColCount + ".");
    }
    mComputedWidths[col] = width;
  }

  /**
   * Sets preferred height of specified row. of difference when resizing.
   * 
   * @param row
   *          index of row. Index must be > 0.
   * @param height
   *          the height in pixels
   * @since 1.0
   */
  public void setPreferredRowHeight(int row, int height)
  {
    if (row >= mRowCount)
    {
      throw new IllegalArgumentException("Column index cannot be greater then "
          + mRowCount + ".");
    }
    mComputedHeights[row] = height;
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
        
        if (-g[i] > g.length-1)
          throw new IllegalArgumentException("Column or Row referencing non existing column or row");

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

  private int[] calcPreferredWidths()
  {
    int[] widths = new int[mColCount];
    ArrayList<View>[] colComponents;

    colComponents = getViewsInColumns();
    for (int i = 0; i < mColCount; i++)
    {
      if (mColWidths[i] > 0) // use specified fixed width
      {
        widths[i] = mColWidths[i];
      }
      else if (mComputedWidths[i] > 0) // has already been calculated before
      {
        widths[i] = mComputedWidths[i];
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
            int width = c.getVisibility() != View.GONE ? c.getMeasuredWidth()
                : WIDTH_ZERO;
            if (width > 0)
            {
              LayoutParams params = (LayoutParams)c.getLayoutParams();
              if (params.w < 0)
                width = -params.w;
              // TODO: consider adding corrections back in
              // else
              // width += constr.wCorrection;
            }
            maxWidth = (width > maxWidth) ? width : maxWidth;
          }
        }
        widths[i] = maxWidth;
      }
    }
    solveCycles(mColWidths, widths);

    return widths;
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
      if (params.x == i)
      {
        ArrayList<View> subList = (ArrayList<View>)list[i];
        if (subList == null)
        {
          subList = new ArrayList<View>();
          list[i] = subList;
        }
        subList.add(view);
      }
    }

    return (ArrayList<View>[])list;
  }

  private int[] calcPreferredHeights()
  {
    int[] heights = new int[mRowCount];

    ArrayList<View>[] rowComponents;

    rowComponents = getViewsInRows();
    for (int i = 0; i < mRowCount; i++)
    {
      if (mRowHeights[i] > 0)// use specified fixed width
      {
        heights[i] = mRowHeights[i];
      }
      else if (mComputedHeights[i] > 0) // has already been calculated before
      {
        heights[i] = mComputedHeights[i];
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
            int height = c.getVisibility() != View.GONE ? c.getMeasuredHeight()
                : HEIGHT_ZERO;
            if (height > 0)
            {
              LayoutParams params = (LayoutParams)c.getLayoutParams();
              if (params.h < 0)
                height = -params.h;
              // TODO: consider adding corrections
              // else
              // height += constr.hCorrection;
            }
            maxHeight = (height > maxHeight) ? height : maxHeight;
          }
        }
        heights[i] = maxHeight;
      }
    }
    solveCycles(mRowHeights, heights);
    return heights;
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
      if (params.y == i)
      {
        ArrayList<View> subList = (ArrayList<View>)list[i];
        if (subList == null)
        {
          subList = new ArrayList<View>();
          list[i] = subList;
        }
        subList.add(view);
      }
    }

    return (ArrayList<View>[])list;
  }

  private void distributeSizeDifference(int desiredLength, int[] lengths,
      int[] weights, int weightSum)
  {
    int preferred = 0;
    int newLength;
    for (int i = lengths.length - 1; i >= 0; i--)
      preferred += lengths[i];

    double unit = ((double)(desiredLength - preferred)) / (double)weightSum;

    for (int i = lengths.length - 1; i >= 0; i--)
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
    if (mCacheColumnsX != null)
      return mCacheColumnsX;
    int[] prefColWidths = calcPreferredWidths();
    // int[] minColWidths = calcMinWidths();

    distributeSizeDifference(targetWidth, prefColWidths, mWidenWeights,
        mWidenWeightsSum);
    int x[] = new int[mColCount + 1]; // only 1: padding right is not necessary
    x[0] = v.getPaddingLeft();

    for (int i = 1; i <= mColCount; i++)
      x[i] = x[i - 1] + prefColWidths[i - 1];
    mCacheColumnsX = x;
    return x;
  }

  /**
   * returns array of y-coordinates of rows. First coordinate is stored in y[0].
   * Reference to this array is cached, so data should not be modified.
   */
  int[] getRowsY(int targetHeight, View v)
  {
    if (mCacheRowsY != null)
      return mCacheRowsY;
    int[] prefRowHeights = calcPreferredHeights();
    // int[] minRowHeights = calcMinHeights();

    distributeSizeDifference(targetHeight, prefRowHeights, mHeightenWeights,
        mHeightenWeightsSum);
    int y[] = new int[mRowCount + 1]; // only 1: padding right is not necessary
    y[0] = v.getPaddingTop();

    for (int i = 1; i <= mRowCount; i++)
      y[i] = y[i - 1] + prefRowHeights[i - 1];
    mCacheRowsY = y;
    return y;
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
      w = a.getInt(R.styleable.HIGlayout_Layout_layout_width, 1);
      h = a.getInt(R.styleable.HIGlayout_Layout_layout_height, 1);
      anchor = a.getString(R.styleable.HIGlayout_Layout_layout_anchor);

      a.recycle();
    }
  }

}
