package edu.mit.mobile.android.livingpostcards;
/*
 * Copyright (C) 2012-2013  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation version 2
 * of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.IOException;

import android.app.Activity;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.scvngr.levelup.views.gallery.AdapterView;
import com.scvngr.levelup.views.gallery.AdapterView.OnItemSelectedListener;
import com.scvngr.levelup.views.gallery.Gallery;

import edu.mit.mobile.android.imagecache.ImageCache;
import edu.mit.mobile.android.imagecache.ImageCache.OnImageLoadListener;
import edu.mit.mobile.android.imagecache.ImageLoaderAdapter;
import edu.mit.mobile.android.imagecache.SimpleThumbnailCursorAdapter;
import edu.mit.mobile.android.livingpostcards.DeleteDialogFragment.OnDeleteListener;
import edu.mit.mobile.android.livingpostcards.auth.Authenticator;
import edu.mit.mobile.android.livingpostcards.data.CardMedia;
import edu.mit.mobile.android.locast.data.Authorable;

public class CardMediaEditFragment extends Fragment implements LoaderCallbacks<Cursor>,
        OnItemSelectedListener, OnImageLoadListener {
    public static final String ARGUMENT_URI = "uri";

    private static final String TAG = CardMediaEditFragment.class.getSimpleName();

    private static final int NO_PENDING = -1;

    private Uri mUri;

    private SimpleThumbnailCursorAdapter mAdapter;
    private Gallery mGallery;
    private ImageView mFrame;

    private ImageCache mImageCache;

    private int mShowBigId;

    private static final int THUMB_W = 160;
    private static final int THUMB_H = 120;

    private static final int HIGHRES_W = 640;
    private static final int HIGHRES_H = 480;

    private static final int HIGHRES_LOAD_DELAY = 100; // ms

    public static CardMediaEditFragment newInstance(Uri cardMedia) {
        final CardMediaEditFragment cmf = new CardMediaEditFragment();

        final Bundle args = new Bundle();
        args.putParcelable(ARGUMENT_URI, cardMedia);
        cmf.setArguments(args);
        return cmf;
    }

    @Override
    public void onAttach(Activity activity) {

        super.onAttach(activity);

        mCMEFHandler = new CMEFHandler(this);
        mImageCache = ImageCache.getInstance(activity);

    }

    @Override
    public void onDetach() {
        super.onDetach();

        mImageCache = null;
        mCMEFHandler.removeMessages(CMEFHandler.MSG_LOAD_HIGHRES);
        mCMEFHandler = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mUri = getArguments().getParcelable(ARGUMENT_URI);

            if (mUri != null) {
                getLoaderManager().initLoader(0, null, this);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        final View v = inflater.inflate(R.layout.card_media_edit_fragment, container, false);

        mAdapter = new SimpleThumbnailCursorAdapter(getActivity(),
                R.layout.card_media_thumb_square, null, new String[] { CardMedia.COL_LOCAL_URL,
                        CardMedia.COL_MEDIA_URL }, new int[] { R.id.card_media_thumbnail,
                        R.id.card_media_thumbnail }, new int[] { R.id.card_media_thumbnail }, 0);

        mGallery = (Gallery) v.findViewById(R.id.gallery);

        mGallery.setAdapter(new ImageLoaderAdapter(getActivity(), mAdapter, mImageCache,
                new int[] { R.id.card_media_thumbnail }, THUMB_W, THUMB_H,
                ImageLoaderAdapter.UNIT_PX, false));

        mGallery.setWrap(true);
        mGallery.setInfiniteScroll(true);

        mGallery.setOnItemSelectedListener(this);

        registerForContextMenu(mGallery);

        mFrame = (ImageView) v.findViewById(R.id.frame);

        return v;
    }

    @Override
    public void onPause() {
        super.onPause();
        mGallery.pause();

        mImageCache.unregisterOnImageLoadListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mImageCache.registerOnImageLoadListener(this);

        mGallery.resume();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {

        return new CursorLoader(getActivity(), mUri, new String[] { CardMedia._ID,
                CardMedia.COL_LOCAL_URL, CardMedia.COL_MEDIA_URL, CardMedia.COL_AUTHOR_URI }, null,
                null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
        mAdapter.swapCursor(c);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    @Override
    public void onItemSelected(AdapterView<?> adapter, View view, int position, long id) {
        // detached
        if (mCMEFHandler == null) {
            return;
        }

        final Cursor item = (Cursor) mAdapter.getItem(position);
        if (item == null) {
            return;
        }
        String uri = item.getString(item.getColumnIndex(CardMedia.COL_LOCAL_URL));
        if (uri == null) {
            uri = item.getString(item.getColumnIndex(CardMedia.COL_MEDIA_URL));
        }
        if (uri == null) {
            return;
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onItemSelected(" + adapter + ", " + view + ", " + position + ", " + id
                    + ")");
        }

        // clear the highres request; if the load has already started, it won't be shown.
        mShowHighresId = NO_PENDING;
        mCMEFHandler.removeMessages(CMEFHandler.MSG_LOAD_HIGHRES);

        mShowBigId = mImageCache.getNewID();

        final Uri imageUri = Uri.parse(uri);
        try {
            final Drawable d = mImageCache.loadImage(mShowBigId, imageUri, THUMB_W, THUMB_H);
            if (d != null) {
                onImageLoaded(mShowBigId, imageUri, d);
            }
        } catch (final IOException e) {
            Log.e(TAG, "Error loading image", e);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapter) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        switch (v.getId()) {
            case R.id.gallery: {
                getActivity().getMenuInflater().inflate(R.menu.context_card_media, menu);
                final Cursor c = mAdapter.getCursor();
                if (c == null) {
                    return;
                }
                AdapterView.AdapterContextMenuInfo info;
                try {
                    info = (AdapterView.AdapterContextMenuInfo) menuInfo;
                } catch (final ClassCastException e) {
                    Log.e(TAG, "bad menuInfo", e);
                    return;
                }

                // the below is a special case due to a bug in the infinite gallery spinner.

                c.moveToPosition(info.position % mAdapter.getCount());

                final String myUserUri = Authenticator.getUserUri(getActivity());

                final boolean isEditable = Authorable.canEdit(myUserUri, c);

                menu.findItem(R.id.delete).setVisible(isEditable);

            }
                break;
            default:
                super.onCreateContextMenu(menu, v, menuInfo);
        }

    }

    private void showDeleteDialog(Uri item) {
        final DeleteDialogFragment del = DeleteDialogFragment.newInstance(item, getActivity()
                .getString(R.string.delete_postcard_image),
                getString(R.string.delete_postcard_image_confirm_message));
        del.registerOnDeleteListener(mOnDeleteListener);
        del.show(getFragmentManager(), "delete-item-dialog");
    }

    private final OnDeleteListener mOnDeleteListener = new OnDeleteListener() {

        @Override
        public void onDelete(Uri item, boolean deleted) {

        }
    };

    private int mShowHighresId;

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (final ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }

        final Uri itemUri = ContentUris.withAppendedId(mUri, info.id);

        switch (item.getItemId()) {
            case R.id.delete:
                showDeleteDialog(itemUri);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onImageLoaded(int id, Uri imageUri, Drawable image) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onImageLoaded(" + id + ", " + imageUri);
        }
        if (id == mShowBigId || id == mShowHighresId) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "ID was either showBigId " + mShowBigId + " or highresId "
                        + mShowHighresId);
            }
            image.setAlpha(255);
            mFrame.setImageDrawable(image);
            if (mShowHighresId != id) {
                // the cast below is not ideal, but MAX_VALUE at ~500 loads / second would yield 50
                // days worth of loads. It's unlikely that this will ever roll over unless the ID
                // generator changes to something other than a simple counter.
                if (!mCMEFHandler.sendMessageDelayed(mCMEFHandler.obtainMessage(
                        CMEFHandler.MSG_LOAD_HIGHRES, mShowBigId, 0, imageUri),
                        HIGHRES_LOAD_DELAY)) {
                    Log.e(TAG, "could not send highres load message");
                }
            } else {
                mShowHighresId = NO_PENDING;
            }
        }
    }

    public void setAnimationTiming(int timing) {
        mGallery.setInterframeDelay(timing);
    }

    public int getAnimationTiming() {
        return (int) mGallery.getInterframeDelay();
    }

    public static class CMEFHandler extends Handler {

        private final CardMediaEditFragment mCmef;
        public static final int MSG_LOAD_HIGHRES = 100;

        public CMEFHandler(CardMediaEditFragment cmef) {
            mCmef = cmef;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LOAD_HIGHRES:
                    mCmef.loadHighres(msg.arg1, (Uri) msg.obj);
                    break;
            }
        }
    }

    private CMEFHandler mCMEFHandler;

    public void loadHighres(int loadId, Uri image) {

        if (mShowBigId == loadId) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Load highres of image ID " + loadId + ": " + image);
            }
            mShowHighresId = mImageCache.getNewID();
            mImageCache.scheduleLoadImage(mShowHighresId, image, HIGHRES_W, HIGHRES_H);
        } else {
            Log.w(TAG, "Did not load highres of image ID " + loadId + ": " + image
                    + " as ID didn't match");
        }
    }

    @Override
    public void onImageLoaded(long id, Uri imageUri, Drawable image) {
        // xxx

    }
}
