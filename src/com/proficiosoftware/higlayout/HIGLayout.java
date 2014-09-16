package com.proficiosoftware.higlayout;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
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

  /* since we number rows and columns from 1, size of these arrays 
   * must be nummber columns + 1*/
  private int[] colWidths;
  private int[] rowHeights;
  private int colCount;
  private int rowCount;

  private ArrayList[] colComponents;
  private ArrayList[] rowComponents;

  private int[] widenWeights;
  private int[] heightenWeights;
  //
  // // Add in preferred heights and widths
  // private int[] preferredWidths;
  // private int[] preferredHeights;

  private int widenWeightsSum = 0;
  private int heightenWeightsSum = 0;

  // private final int invisible = 0;;

  /* Following variables are for caching of computations: */
  private int cacheColumnsX[];
  private int cacheRowsY[];

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

    try
    {
      String colw = a.getString(R.styleable.HIGlayout_column_widths);
      if (colw == null)
        throw new IllegalArgumentException("Missing attribute: column_widths");
      colWidths = stringToIntArray(colw);
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
      rowHeights = stringToIntArray(rowh);
    }
    catch (NumberFormatException nfe)
    {
      throw new IllegalArgumentException(
          "Illegal value in row_heights attribute.", nfe);
    }

    colCount = colWidths.length;
    rowCount = rowHeights.length;

    try
    {
      String colw = a.getString(R.styleable.HIGlayout_column_weights);
      if (colw == null)
        widenWeights = new int[colWidths.length];
      else
        widenWeights = stringToIntArray(colw);
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
        heightenWeights = new int[rowHeights.length];
      else
        heightenWeights = stringToIntArray(roww);
    }
    catch (NumberFormatException nfe)
    {
      throw new IllegalArgumentException(
          "Illegal value in row_weights attribute.", nfe);
    }

    if (heightenWeights.length != rowCount)
      throw new IllegalArgumentException(
          "Weights list must match number of rows");
    if (widenWeights.length != colCount)
      throw new IllegalArgumentException(
          "Weights list must match number of columns");

    widenWeightsSum = 0;
    for (int i = 1; i <= colCount; i++)
      widenWeightsSum += widenWeights[i];

    heightenWeightsSum = 0;
    for (int i = 1; i <= rowCount; i++)
      heightenWeightsSum += heightenWeights[i];

    colComponents = new ArrayList[colCount];
    rowComponents = new ArrayList[rowCount];

    // TODO: add column/row weights
    // a.getString(R.styleable.HIGlayout_column_weights);
    // a.getString(R.styleable.HIGlayout_row_weights);

    a.recycle();
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
    colCount = widths.length;
    rowCount = heights.length;

    colWidths = new int[colCount];
    System.arraycopy(widths, 0, colWidths, 0, colCount);
    rowHeights = new int[rowCount];
    System.arraycopy(heights, 0, rowHeights, 0, rowCount);

    widenWeights = new int[colCount];
    heightenWeights = new int[rowCount];

    // preferredWidths = new int[colCount];
    // preferredHeights = new int[rowCount];

    colComponents = new ArrayList[colCount];
    rowComponents = new ArrayList[rowCount];
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b)
  {
    final int count = getChildCount();

    // get the available size of child view
    int sizeWidth = this.getMeasuredWidth();
    sizeWidth -= this.getPaddingLeft() + this.getPaddingRight();
    int sizeHeight = this.getMeasuredHeight();
    sizeHeight -= this.getPaddingTop() + this.getPaddingBottom();

    // TODO: check what this does.
    int x[] = getColumnsX(sizeWidth,this);
    int y[] = getRowsY(sizeHeight,this);

    for (int i = 0; i < count; i++)
    {
      // Get the View to position
      View child = getChildAt(i);

      // Get the Views specific properties (like position and anchoring)
      LayoutParams c = (LayoutParams)child.getLayoutParams();
      /* first we centre component into cell */

      child.measure(
          MeasureSpec.makeMeasureSpec(sizeWidth, MeasureSpec.AT_MOST),
          MeasureSpec.makeMeasureSpec(sizeHeight, MeasureSpec.AT_MOST));

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
    if (colCount < col)
    {
      colCount = col;
    }
    if (colWidths.length <= col)
    {
      colWidths = (int[])reallocArray(colWidths, colCount + 3);
      widenWeights = (int[])reallocArray(widenWeights, colCount + 3);
      colComponents = (ArrayList[])reallocArray(colComponents, colCount + 3);
      // preferredWidths = (int[])reallocArray(preferredWidths, colCount + 3);
    }
    colWidths[col] = width;
  }

  /**
   * Sets row height, realloc arrays if there is need.
   * 
   * @since 0.97
   */
  public void setRowHeight(int row, int height)
  {
    if (rowCount < row)
    {
      rowCount = row;
    }
    if (rowHeights.length <= row)
    {
      rowHeights = (int[])reallocArray(rowHeights, rowCount + 3);
      heightenWeights = (int[])reallocArray(heightenWeights, rowCount + 3);
      rowComponents = (ArrayList[])reallocArray(rowComponents, rowCount + 3);
    }
    rowHeights[row] = height;
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
  // public void setPreferredColumnWidth(int col, int width)
  // {
  // if (col > colCount)
  // {
  // throw new IllegalArgumentException("Column index cannot be greater then "
  // + colCount + ".");
  // }
  // preferredWidths[col] = width;
  // }

  /**
   * Sets preferred height of specified row. of difference when resizing.
   * 
   * @param row
   *          index of row. Index must be > 0.
   * @param height
   *          the height in pixels
   * @since 1.0
   */
  // public void setPreferredRowHeight(int row, int height)
  // {
  // if (row > rowCount)
  // {
  // throw new IllegalArgumentException("Column index cannot be greater then "
  // + rowCount + ".");
  // }
  // preferredHeights[row] = height;
  // }

  /**
   * Sets weight of specified column. Weight determines distribution of
   * difference when resizing.
   * 
   * @param col
   *          index of column. Index must be > 0.
   */
  public void setColumnWeight(int col, int weight)
  {
    if (col > colCount)
    {
      throw new RuntimeException("Column index cannot be greater then "
          + colCount + ".");
    }
    widenWeights[col] = weight;
    widenWeightsSum = 0;
    for (int i = 1; i <= colCount; i++)
      widenWeightsSum += widenWeights[i];
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
    if (row > rowCount)
    {
      throw new RuntimeException("Column index cannot be greater then "
          + rowCount + ".");
    }
    heightenWeights[row] = weight;
    heightenWeightsSum = 0;
    for (int i = 1; i <= rowCount; i++)
      heightenWeightsSum += heightenWeights[i];
  }

  private void solveCycles(int g[], int lengths[])
  {
    /* TODO: handle cycles of length 1*/
    int path[] = new int[g.length];
    int stackptr = 0;

    /* marks of visited vertices. 0 - not visited, 1 - visited, 2 - visited and set final value */
    byte visited[] = new byte[g.length];
    for (int i = g.length - 1; i > 0; i--)
    {
      if ((g[i] < 0) && (visited[i] == 0))
      {
        int current = i;

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

  private int[] calcMinWidths()
  {
    int[] widths = new int[colCount + 1];
    for (int i = 1; i <= colCount; i++)
    {
      if (colWidths[i] > 0)
      {
        widths[i] = colWidths[i];
      }
      else
      {
        ArrayList<View> iComps = colComponents[i];
        int maxWidth = 0;
        if (iComps != null)
        {
          for (int j = iComps.size() - 1; j > -1; j--)
          {
            View c = iComps.get(j);
            int width = c.isVisible() ? c.getMinimumSize().width : 0;

            if (width > 0)
            {
              HIGConstraints constr = components.get(c);
              if (constr.w < 0)
                width = -constr.w;
              else
                width += constr.wCorrection;
            }
            maxWidth = (width > maxWidth) ? width : maxWidth;
          }
        }
        widths[i] = maxWidth;
      }
    }
    solveCycles(colWidths, widths);

    return widths;
  }

  private int[] calcMinHeights()
  {
    int[] heights = new int[rowCount + 1];
    for (int i = 1; i <= rowCount; i++)
    {
      if (rowHeights[i] > 0)
      {
        heights[i] = rowHeights[i];
      }
      else
      {
        int maxHeight = 0;
        ArrayList iComps = rowComponents[i];
        if (iComps != null)
        {
          for (int j = iComps.size() - 1; j > -1; j--)
          {
            View c = iComps.get(j);
            int height = c.isVisible() ? c.getMinimumSize().height : invisible;
            if (height > 0)
            {
              HIGConstraints constr = components.get(c);
              if (constr.h < 0)
                height = -constr.h;
              else
                height += constr.hCorrection;
            }
            maxHeight = (height > maxHeight) ? height : maxHeight;
          }
        }
        heights[i] = maxHeight;
      }
    }
    solveCycles(rowHeights, heights);

    return heights;
  }

  private int[] calcPreferredWidths()
  {
    int[] widths = new int[colCount + 1];
    for (int i = 1; i <= colCount; i++)
    {
      if (colWidths[i] > 0)
      {
        widths[i] = colWidths[i];
      }
      else if (preferredWidths[i] > 0)
      {
        widths[i] = preferredWidths[i];
      }
      else
      {
        int maxWidth = 0;
        ArrayList iComps = colComponents[i];
        if (iComps != null)
        {
          for (int j = iComps.size() - 1; j > -1; j--)
          {
            View c = iComps.get(j);
            int width = c.isVisible() ? c.getPreferredSize().width : invisible;
            if (width > 0)
            {
              HIGConstraints constr = components.get(c);
              if (constr.w < 0)
                width = -constr.w;
              else
                width += constr.wCorrection;
            }
            maxWidth = (width > maxWidth) ? width : maxWidth;
          }
        }
        widths[i] = maxWidth;
      }
    }
    solveCycles(colWidths, widths);

    return widths;
  }

  private int[] calcPreferredHeights()
  {
    int[] heights = new int[rowCount + 1];
    for (int i = 1; i <= rowCount; i++)
    {
      if (rowHeights[i] > 0)
      {
        heights[i] = rowHeights[i];
      }
      else if (preferredHeights[i] > 0)
      {
        heights[i] = preferredHeights[i];
      }
      else
      {
        ArrayList iComps = rowComponents[i];
        int maxHeight = 0;
        if (iComps != null)
        {
          for (int j = iComps.size() - 1; j >= 0; j--)
          {
            View c = iComps.get(j);
            int height = c.isVisible() ? c.getPreferredSize().height
                : invisible;
            if (height > 0)
            {
              HIGConstraints constr = components.get(c);
              if (constr.h < 0)
                height = -constr.h;
              else
                height += constr.hCorrection;
            }
            maxHeight = (height > maxHeight) ? height : maxHeight;
          }
        }
        heights[i] = maxHeight;
      }
    }
    solveCycles(rowHeights, heights);
    return heights;
  }

  private void distributeSizeDifference(int desiredLength, int[] lengths,
      int[] minLengths, int[] weights, int weightSum)
  {
    int preferred = 0;
    int newLength;
    for (int i = lengths.length - 1; i > 0; i--)
      preferred += lengths[i];

    double unit = ((double)(desiredLength - preferred)) / (double)weightSum;

    for (int i = lengths.length - 1; i > 0; i--)
    {
      newLength = lengths[i] + (int)(unit * (double)weights[i]);
      lengths[i] = (newLength > minLengths[i]) ? newLength : minLengths[i];
    }
  }

  /**
   * Calculates the preferred size dimensions for the specified container given
   * the components in the specified parent container.
   * 
   * @param parent
   *          the component to be laid out
   *
   * @see #minimumLayoutSize
   */
  public Dimension preferredLayoutSize(Container target)
  {
    synchronized (target.getTreeLock())
    {
      if (cachePreferredLayoutSize != null)
        return cachePreferredLayoutSize;
      final int[] prefColWidths = calcPreferredWidths();
      final int[] prefRowHeights = calcPreferredHeights();
      Insets insets = target.getInsets();
      int w = insets.left + insets.right;
      int h = insets.top + insets.bottom;
      for (int i = 1; i <= colCount; i++)
        w += prefColWidths[i];
      for (int i = 1; i <= rowCount; i++)
        h += prefRowHeights[i];
      cachePreferredLayoutSize = new Dimension(w, h);
      return cachePreferredLayoutSize;
    }

  }

  /**
   * returns array of x-coordinates of columns. First coordinate is stored in
   * x[0] Reference to this array is cached, so data should not be modified.
   */
  int[] getColumnsX(int targetWidth, View v)
  {
    if (cacheColumnsX != null)
      return cacheColumnsX;
    int[] prefColWidths = calcPreferredWidths();
    int[] minColWidths = calcMinWidths();

    distributeSizeDifference(targetWidth, prefColWidths, minColWidths,
        widenWeights, widenWeightsSum);
    int x[] = new int[colCount + 2];
    x[0] = v.getPaddingLeft();

    for (int i = 1; i <= colCount; i++)
      x[i] = x[i - 1] + prefColWidths[i - 1];
    cacheColumnsX = x;
    return x;
  }

  /**
   * returns array of y-coordinates of rows. First coordinate is stored in y[0].
   * Reference to this array is cached, so data should not be modified.
   */
  int[] getRowsY(int targetHeight, View v)
  {
    if (cacheRowsY != null)
      return cacheRowsY;
    int[] prefRowHeights = calcPreferredHeights();
    int[] minRowHeights = calcMinHeights();

    distributeSizeDifference(targetHeight, prefRowHeights, minRowHeights,
        heightenWeights, heightenWeightsSum);
    int y[] = new int[rowCount + 2];
    y[0] = v.getPaddingTop();

    for (int i = 1; i <= rowCount; i++)
      y[i] = y[i - 1] + prefRowHeights[i - 1];
    cacheRowsY = y;
    return y;
  }

  public static class LayoutParams extends ViewGroup.MarginLayoutParams
  {
    public int x;
    public int y;
    public int w;
    public int h;
    public String anchor;

    public LayoutParams(Context c, AttributeSet attrs)
    {
      super(c, attrs);
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
