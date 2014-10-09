package com.proficiosoftware.higlayout;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.util.MonthDisplayHelper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

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
 * ChangeLog
 * v 1.1 16/SEP/2014
 * 
 * - Ported to Android
 * - Removed Java specific methods
 * - Added comments 
 * 
 * @see cz.autel.dmi.HIGConstraints
 * @version 1.1 16/SEP/2014
 * @author Daniel Michalik (dmi@autel.cz),
 *         Romano Caserta (caserta@disy.net),
 *         Frank Behrens (frank@pinky.sax.de),
 *         Sven Behrens (behrens@disy.net),
 *         Alberto Ricart (aricart@smartsoft.com),
 *         Peter Reilly (Peter.Reilly@marconi.com),
 *         Jaap Geurts (jaapg@gmx.net)
 */
public class HIGLayout extends ViewGroup
{

  private static final int WIDTH_ZERO = 0;
  private static final int HEIGHT_ZERO = 0;
  private static final String LOGTAG = "HIGLayout";

  // used to keep the heights and widths supplied in XML
  private int[] mColWidths;
  private int[] mRowHeights;
  private int mColCount;
  private int mRowCount;

  // used to keep the weights supplied in XML
  private int[] mWidenWeights;
  private int[] mHeightenWeights;

  // Display a debug grid
  private boolean mShowGrid = false;

  // Holds the computed values after scaling to the correct with and height of
  // the parent layout
  private int[] mComputedWidths;
  private int[] mComputedHeights;

  private int mWidenWeightsSum = 0;
  private int mHeightenWeightsSum = 0;

  // Holds the pixel positions of where the columns/row start
  private int[] cacheColumnsX;
  private int[] cacheRowsY;
  private Paint mGridPaint = null;

  // TODO: remove before release
  private int mPassCount = 0;

  public HIGLayout(Context context)
  {
    super(context);

  }

  public HIGLayout(Context context, AttributeSet attrs)
  {
    this(context, attrs, 0);
  }

  /**
   * Constructs a new HIGLayout with the requested parameters.
   * Currently paramaters are only read from XML and cannot be set
   * programmatically.
   * 
   * @param context
   * @param attrs
   * @param defStyleAttr
   */
  public HIGLayout(Context context, AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);

    // Gets the styles from XML file

    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.HIGlayout,
        defStyleAttr, 0);

    try
    {
      String colw = a.getString(R.styleable.HIGlayout_column_widths);
      // FIXME: RuntimeException is thrown when showing in eclipse designer
      if (colw == null)
        throw new RuntimeException("Missing attribute: column_widths");
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
        throw new RuntimeException("Missing attribute: row_heights");
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
    {
      setWillNotDraw(false);
      mGridPaint = new Paint();
      mGridPaint.setColor(Color.GREEN);
      mGridPaint.setStyle(Paint.Style.STROKE);

      Log.i(LOGTAG, "Grid visualization enabled");
    }

    if (mHeightenWeights.length != mRowCount)
      throw new IllegalArgumentException(
          "RowWeights list must match number of rows");
    if (mWidenWeights.length != mColCount)
      throw new IllegalArgumentException(
          "ColumnWeights list must match number of columns");

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

  /**
   * Positions all direct children.
   * Column and Row sizes have been precalculated during the onMeasure() phase
   * and will be used here. Check onMeasure() for more details
   * General algorithm.
   * 1. Get Column and Row absolute positions
   * 2. For each child, find its position in the grid and position it based
   *    on absolute col/row values.
   * 3. Initial position is in the center of the cell.
   * 4. After than inspect the anchor and widen the child accordingly
   * 5. Remeasure the child to make sure it knows its new size. This is
   *    necessary because e.g. Button doesn't layout the text correctly if
   *    resized after measuring.   
   */
  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b)
  {

    int w = r - l;
    int h = b - t;
    Log.d(LOGTAG, "Imposed dimension: " + w + "x" + h);
    // TODO: should I position myself as well or just my children?
    final int count = getChildCount();

    // Get the absolute column/row coordinates
    int x[] = getColumnsX();
    int y[] = getRowsY();

    // For each child in the view
    for (int i = 0; i < count; i++)
    {
      // Get the View to position
      View child = getChildAt(i);

      // Get the Views specific properties (like position and anchoring)
      LayoutParams c = (LayoutParams)child.getLayoutParams();

      /* first we centre the component into its cell */
      int width = child.getMeasuredWidth();
      int height = child.getMeasuredHeight();
      int cellw;
      int cellh;
      if (c.w < 0) // use fixed width specified in XML.
      {
        width = -c.w;
        cellw = x[c.x + 1] - x[c.x];
      }
      else
      {
        // compute width of the child
        cellw = x[c.x + c.w] - x[c.x];
      }

      if (c.h < 0)// use fixed height specified in XML.
      {
        height = -c.h;
        cellh = y[c.y + 1] - y[c.y];
      }
      else
      {
        // compute height of the child
        cellh = y[c.y + c.h] - y[c.y];
      }

      boolean allowXSize = true;
      boolean allowYSize = true;

      // position the child in the centre
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

      // Force child remeasure to ensure it shows correctly
      child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
      // Finally position the child
      child.layout((int)compx, (int)compy, (int)(compx + width),
          (int)(compy + height));
    }
  }

  /**
   * Calculated the grid.
   */
  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
  {
    Log.d(LOGTAG, "Pass: " + mPassCount);
    mPassCount++;

    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    int imposedWidth = MeasureSpec.getSize(widthMeasureSpec);
    int imposedHeight = MeasureSpec.getSize(heightMeasureSpec);

    // First ask all child views to measure and give us their preferred sizes
    // TODO: should measure child with margins
    measureChildren(widthMeasureSpec, heightMeasureSpec);

    ViewParent v = getParent();
    Log.d(LOGTAG, "Parent: " + v.getClass().getName());

    // // DEBUG
    switch(heightMode)
    {
      case MeasureSpec.UNSPECIFIED:
        Log.d(LOGTAG, "UNSPECIFIED");
        break;
      case MeasureSpec.EXACTLY:
        Log.d(LOGTAG, "EXACTLY");
        break;
      case MeasureSpec.AT_MOST:
        Log.d(LOGTAG, "AT_MOST");
        break;
    }
    Log.d(LOGTAG, "Suggested Dimension: " + imposedWidth + "x" + imposedHeight);

    // calculate our desired widths of the components without our padding
    int calculatedWidth = calcWidths();
    int calculatedHeight = calcHeights();

    int cw = calculatedWidth + getPaddingLeft() + getPaddingRight();
    int ch = calculatedHeight + getPaddingTop() + getPaddingBottom();
    Log.d(LOGTAG, "Minimum Dimension: " + cw + "x" + ch);

    // Adjust for parameters imposed by our parent.
    // set preferredWidth (without padding).
    int preferredWidth = calculatedWidth;
    int preferredHeight = calculatedHeight;
    if (widthMode == MeasureSpec.EXACTLY
        || (widthMode == MeasureSpec.AT_MOST && preferredWidth > imposedWidth))
      preferredWidth = imposedWidth - getPaddingLeft() - getPaddingTop();
    if (heightMode == MeasureSpec.EXACTLY
        || (heightMode == MeasureSpec.AT_MOST && preferredHeight > imposedHeight))
      preferredHeight = imposedHeight - getPaddingTop() - getPaddingBottom();

    // Scale according the preferredWidth/Height
    distributeSizeDifference(preferredWidth, mComputedWidths, mWidenWeights,
        mWidenWeightsSum);
    distributeSizeDifference(preferredHeight, mComputedHeights,
        mHeightenWeights, mHeightenWeightsSum);

    // This is the width our view occupies
    int finalWidth = preferredWidth + getPaddingLeft() + getPaddingRight();
    int finalHeight = preferredHeight + getPaddingTop() + getPaddingBottom();

    Log.d(LOGTAG, "Final Dimension: " + finalWidth + "x" + finalHeight);
    setMeasuredDimension(finalWidth, finalHeight);
  }

  /**
   * Draw debug grid. Is only called when "show_debug" property is set to true
   */
  @Override
  protected void onDraw(Canvas canvas)
  {
    super.onDraw(canvas);

    if (!mShowGrid)
      return;

    int height = this.getHeight();
    int width = this.getWidth();
    float ht_px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1,
        getResources().getDisplayMetrics());
    mGridPaint.setStrokeWidth(ht_px);

    int x[] = getColumnsX();
    for (int i = 0; i < x.length; i++)
      canvas.drawLine(x[i], 0, x[i], height, mGridPaint);

    int y[] = getRowsY();
    for (int i = 0; i < y.length; i++)
      canvas.drawLine(0, y[i], width, y[i], mGridPaint);

  }

  @Override
  protected boolean checkLayoutParams(ViewGroup.LayoutParams p)
  {
    return p instanceof LayoutParams;
  }

  @Override
  protected LayoutParams generateDefaultLayoutParams()
  {
    return new LayoutParams();
  }

  @Override
  public LayoutParams generateLayoutParams(AttributeSet attrs)
  {
    return new LayoutParams(getContext(), attrs);
  }

  @Override
  protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p)
  {
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

  /**
   * Calculate the absolute positions of each column by adding column widths
   * 
   * @return
   */
  private int[] getColumnsX()
  {
    if (cacheColumnsX != null)
      return cacheColumnsX;

    int x[] = new int[mColCount + 1]; // add 1 left for padding left
    x[0] = this.getPaddingLeft();

    for (int i = 1; i <= mColCount; i++)
      x[i] = x[i - 1] + mComputedWidths[i - 1];

    cacheColumnsX = x;
    return x;
  }

  /**
   * Calculate the absolute positions of each row by adding row heights
   * 
   * @return
   */
  private int[] getRowsY()
  {
    if (cacheRowsY != null)
      return cacheRowsY;
    int y[] = new int[mRowCount + 1];
    y[0] = this.getPaddingTop();

    for (int i = 1; i <= mRowCount; i++)
      y[i] = y[i - 1] + mComputedHeights[i - 1];

    cacheRowsY = y;
    return y;
  }

  /**
   * Calculate any references to other columns/rows in the list. The purpose
   * is to make sure that all paths and cycles share the same width/height. 
   * @param g
   * @param lengths
   */
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
          throw new RuntimeException(
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

  /**
   * Calculate the widths of each column by finding the maximum width of all
   * components in that column
   */
  private int calcWidths()
  {
    // finds max with for
    int[] widths = new int[mColCount];
    int totalWidth = 0;
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
            View childView = iComps.get(j);
            int width = childView.getVisibility() != View.GONE ? childView
                .getMeasuredWidth() : WIDTH_ZERO;
            LayoutParams params = (LayoutParams)childView.getLayoutParams();
            if (params.w < 0)
              width = -params.w;
            maxWidth = (width > maxWidth) ? width : maxWidth;
          }
        }
        widths[i] = maxWidth;
      }
    }
    solveCycles(mColWidths, widths);

    mComputedWidths = widths;
    for (int w : mComputedWidths)
      totalWidth += w;

    return totalWidth;
  }

  /**
   * Calculate the heights of each row by finding the maximum height of all
   * components in that row
   */

  private int calcHeights()
  {
    int[] heights = new int[mRowCount];
    int totalHeight = 0;

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
            View childView = iComps.get(j);

            int height = childView.getVisibility() != View.GONE ? childView
                .getMeasuredHeight() : HEIGHT_ZERO;
            LayoutParams params = (LayoutParams)childView.getLayoutParams();
            if (params.h < 0)
              height = -params.h;
            maxHeight = (height > maxHeight) ? height : maxHeight;
          }
        }
        heights[i] = maxHeight;
      }
    }
    solveCycles(mRowHeights, heights);

    mComputedHeights = heights;

    for (int h : mComputedHeights)
      totalHeight += h;

    return totalHeight;
  }

  /**
   * Returns an array of views ordered by column. 
   * @return
   */
  @SuppressWarnings("unchecked")
  private ArrayList<View>[] getViewsInColumns()
  {
    ArrayList<?>[] list = new ArrayList<?>[mColCount];

    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++)
    {
      View view = getChildAt(i);
      LayoutParams params = (LayoutParams)view.getLayoutParams();

      if (params.w == 1)
      {
        // only add objects when they occupy a single column
        ArrayList<View> subList = (ArrayList<View>)list[params.x];
        if (subList == null)
        {
          subList = new ArrayList<View>();
          list[params.x] = subList;
        }
        subList.add(view);
      }
    }

    return (ArrayList<View>[])list;
  }

  /**
   * Returns an array of views ordered by row.
   * @return
   */
  @SuppressWarnings("unchecked")
  private ArrayList<View>[] getViewsInRows()
  {
    ArrayList<?>[] list = new ArrayList<?>[mRowCount];

    int childCount = getChildCount();
    for (int i = 0; i < childCount; i++)
    {
      View view = getChildAt(i);
      LayoutParams params = (LayoutParams)view.getLayoutParams();
      if (params.h == 1)
      {
        // only add objects when they occupy a single row
        ArrayList<View> subList = (ArrayList<View>)list[params.y];
        if (subList == null)
        {
          subList = new ArrayList<View>();
          list[params.y] = subList;
        }
        subList.add(view);
      }
    }
    return (ArrayList<View>[])list;
  }

  /**
   * Takes an array of maximum widths of a column/row and stretches it out
   * according to the desiredLength and using weights. 
   * @param desiredLength
   * @param lengths
   * @param weights
   * @param weightSum
   */
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
      lengths[i] = newLength;
    }
  }

  // TODO: handle margins.
  public static class LayoutParams extends ViewGroup.MarginLayoutParams
  {
    public int x = 0;
    public int y = 0;
    public int w = 1;
    public int h = 1;
    public String anchor = "lrtb";

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
      if (anchor == null)
        anchor = "lrtb";

      a.recycle();
    }
  }

}
