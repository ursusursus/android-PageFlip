/*
 * Copyright (C) 2016 eschao <esc.chao@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.eschao.android.widget.sample.pageflip;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.eschao.android.widget.pageflip.Page;
import com.eschao.android.widget.pageflip.PageFlip;
import com.eschao.android.widget.pageflip.PageFlipState;

/**
 * Single page render
 * <p>
 * Every page need 2 texture in single page mode:
 * <ul>
 * <li>First texture: current page content</li>
 * <li>Back texture: back of front content, it is same with first texture
 * </li>
 * <li>Second texture: next page content</li>
 * </ul>
 * </p>
 *
 * @author eschao
 */

public class SinglePageRender extends PageRender {

    private final Paint mPaint;

    //private final Handler mHandler2 = new Handler();
    private final PageFlipView mPageFlipView;

    /**
     * Constructor
     *
     * @see {@link #PageRender(Context, PageFlip, Handler, int)}
     */
    public SinglePageRender(Context context, PageFlip pageFlip,
                            Handler handler, int pageNo, PageFlipView pageFlipView) {
        super(context, pageFlip, handler, pageNo);
        mPageFlipView = pageFlipView;

        Paint p = new Paint();
        p.setFilterBitmap(true);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(1);
        p.setAntiAlias(true);
        p.setShadowLayer(5.0f, 8.0f, 8.0f, Color.BLACK);
        mPaint = p;
    }

    /**
     * Draw frame
     */
    @Override public void onDrawFrame() {
//        Log.d("Default", "onDrawFrame=" + mDrawCommand);
        // 1. delete unused textures
        mPageFlip.deleteUnusedTextures();
        final Page page = mPageFlip.getFirstPage();
        Log.d("Default", "firstPage=" + page);
        Log.d("Default", "secondPage=" + mPageFlip.getSecondPage());

        // 2. handle drawing command triggered from finger moving and animating
        if (mDrawCommand == DRAW_MOVING_FRAME ||
                mDrawCommand == DRAW_ANIMATING_FRAME) {
            // Log.d("Default", "DRAW_MOVING_FRAME");
            // is forward flip
            if (mPageFlip.getFlipState() == PageFlipState.FORWARD_FLIP) {
                // check if second texture of first page is valid, if not,
                // create new one
                if (!page.isSecondTextureSet()) {
                    Log.d("Default", "redraw A=" + Thread.currentThread().getName());
                    drawPage(mPageNo + 1, new Runnable() {
                        @Override public void run() {
                            queueOnGlThread(new Runnable() {
                                @Override public void run() {
                                    Log.d("Default", "redraw A=" + Thread.currentThread().getName());
                                    page.setSecondTexture(mBitmap);
                                    mPageFlip.drawFlipFrame();
                                }
                            });
                        }
                    });
                    page.setSecondTexture(mBitmap);
                    mPageFlip.drawFlipFrame();

                } else {
                    mPageFlip.drawFlipFrame();
                }
            } else if (!page.isFirstTextureSet()) {
                // in backward flip, check first texture of first page is valid
                Log.d("Default", "redraw B=" + Thread.currentThread().getName());
                drawPage(--mPageNo, new Runnable() {
                    @Override public void run() {
                        queueOnGlThread(new Runnable() {
                            @Override public void run() {
                                Log.d("Default", "redraw B" + Thread.currentThread().getName());
                                page.setFirstTexture(mBitmap);
                                mPageFlip.drawFlipFrame();
                            }
                        });
                        page.setFirstTexture(mBitmap);
                        mPageFlip.drawFlipFrame();
                    }
                });
            } else {
                // draw frame for page flip
                mPageFlip.drawFlipFrame();

            }
        }
        // draw stationary page without flipping
        else if (mDrawCommand == DRAW_FULL_PAGE) {
            Log.d("Default", "DRAW_FULL_PAGE");
            if (!page.isFirstTextureSet()) {
                Log.d("Default", "redraw C=" + Thread.currentThread().getName());
                drawPage(mPageNo, new Runnable() {
                    @Override public void run() {
                        queueOnGlThread(new Runnable() {
                            @Override public void run() {
                                Log.d("Default", "redraw C=" + Thread.currentThread().getName());
                                page.setFirstTexture(mBitmap);
                                mPageFlip.drawFlipFrame();
                            }
                        });
                    }
                });
                page.setFirstTexture(mBitmap);
                mPageFlip.drawFlipFrame();

            } else {
                mPageFlip.drawPageFrame();

            }

        }

        // 3. send message to main thread to notify drawing is ended so that
        // we can continue to calculate next animation frame if need.
        // Remember: the drawing operation is always in GL thread instead of
        // main thread
        Message msg = Message.obtain();
        msg.what = MSG_ENDED_DRAWING_FRAME;
        msg.arg1 = mDrawCommand;
        mHandler.sendMessage(msg);
    }

    private void queueOnGlThread(Runnable runnable) {
        mPageFlipView.queueEvent(runnable);
        mPageFlipView.requestRender();
    }

    /**
     * Handle GL surface is changed
     *
     * @param width  surface width
     * @param height surface height
     */
    @Override public void onSurfaceChanged(int width, int height) {
        // recycle bitmap resources if need
        if (mBackgroundBitmap != null) {
            mBackgroundBitmap.recycle();
        }

        if (mBitmap != null) {
            mBitmap.recycle();
        }

        // create bitmap and canvas for page
        //mBackgroundBitmap = background;
        Page page = mPageFlip.getFirstPage();
        mBitmap = Bitmap.createBitmap((int) page.width(), (int) page.height(),
                Bitmap.Config.ARGB_8888);
        mCanvas.setBitmap(mBitmap);
        LoadBitmapTask.get(mContext).set(width, height, 1);
    }

    /**
     * Handle ended drawing event
     * In here, we only tackle the animation drawing event, If we need to
     * continue requesting render, please return true. Remember this function
     * will be called in main thread
     *
     * @param what event type
     * @return ture if need render again
     */
    @Override public boolean onEndedDrawing(int what) {
        if (what == DRAW_ANIMATING_FRAME) {
            boolean isAnimating = mPageFlip.animating();
            // continue animating
            if (isAnimating) {
                mDrawCommand = DRAW_ANIMATING_FRAME;
                return true;
            }
            // animation is finished
            else {
                final PageFlipState state = mPageFlip.getFlipState();
                // update page number for backward flip
                if (state == PageFlipState.END_WITH_BACKWARD) {
                    // don't do anything on page number since mPageNo is always
                    // represents the FIRST_TEXTURE no;
                }
                // update page number and switch textures for forward flip
                else if (state == PageFlipState.END_WITH_FORWARD) {
                    mPageFlip.getFirstPage().setFirstTextureWithSecond();
                    mPageNo++;
                }

                mDrawCommand = DRAW_FULL_PAGE;
                return true;
            }
        }
        return false;
    }

    /**
     * Draw page content
     *
     * @param number page number
     */
    private void drawPage(final int number, final Runnable end) {
        Log.d("Default", "number=" + number);
        final Canvas canvas = mCanvas;
        final Paint p = mPaint;
        final int width = canvas.getWidth();
        final int height = canvas.getHeight();

        canvas.drawColor(Color.CYAN);
        // 1. draw background bitmap
        LoadBitmapTask.get(mContext).getBitmap(number, new LoadBitmapTask.BitmapLoaded() {
            @Override public void onBitmapLoaded(final Bitmap bitmap) {
                Log.d("Default", "onBitmapLoaded=" + number + " t=" + Thread.currentThread().getName());
                Rect rect = new Rect(0, 0, width, height);
                canvas.drawBitmap(bitmap, null, rect, mPaint);
                bitmap.recycle();

                // 2. draw page number
                p.setTextSize(calcFontSize(80));
                String text = String.valueOf(number);
                float textWidth = p.measureText(text);
                float y = height - p.getTextSize() - 20;
                canvas.drawText(text, (width - textWidth) / 2, y, p);

                if (number <= 1) {
                    String firstPage = "The First Page";
                    p.setTextSize(calcFontSize(16));
                    float w = p.measureText(firstPage);
                    float h = p.getTextSize();
                    canvas.drawText(firstPage, (width - w) / 2, y + 5 + h, p);
                } else if (number >= MAX_PAGES) {
                    String lastPage = "The Last Page";
                    p.setTextSize(calcFontSize(16));
                    float w = p.measureText(lastPage);
                    float h = p.getTextSize();
                    canvas.drawText(lastPage, (width - w) / 2, y + 5 + h, p);
                }
                end.run();
//                mHandler2.post(new Runnable() {
//                    @Override public void run() {
//                        end.run();
//                    }
//                });
            }
        });

        // 2. draw page number
        p.setTextSize(calcFontSize(80));
        String text = String.valueOf(number);
        float textWidth = p.measureText(text);
        float y = height - p.getTextSize() - 20;
        canvas.drawText(text, (width - textWidth) / 2, y, p);

        if (number <= 1) {
            String firstPage = "The First Page";
            p.setTextSize(calcFontSize(16));
            float w = p.measureText(firstPage);
            float h = p.getTextSize();
            canvas.drawText(firstPage, (width - w) / 2, y + 5 + h, p);
        } else if (number >= MAX_PAGES) {
            String lastPage = "The Last Page";
            p.setTextSize(calcFontSize(16));
            float w = p.measureText(lastPage);
            float h = p.getTextSize();
            canvas.drawText(lastPage, (width - w) / 2, y + 5 + h, p);
        }
        end.run();
    }

    /**
     * If page can flip forward
     *
     * @return true if it can flip forward
     */
    public boolean canFlipForward() {
        return (mPageNo < MAX_PAGES - 1);
    }

    /**
     * If page can flip backward
     *
     * @return true if it can flip backward
     */
    public boolean canFlipBackward() {
        if (mPageNo > 0) {
            mPageFlip.getFirstPage().setSecondTextureWithFirst();
            return true;
        } else {
            return false;
        }
    }
}
