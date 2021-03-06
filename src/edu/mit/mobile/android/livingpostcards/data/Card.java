package edu.mit.mobile.android.livingpostcards.data;
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

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import edu.mit.mobile.android.content.DBSortOrder;
import edu.mit.mobile.android.content.ForeignKeyManager;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.content.UriPath;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.IntegerColumn;
import edu.mit.mobile.android.content.column.TextColumn;
import edu.mit.mobile.android.flipr.R;
import edu.mit.mobile.android.livingpostcards.auth.Authenticator;
import edu.mit.mobile.android.locast.app.LocastApplication;
import edu.mit.mobile.android.locast.data.AbsResourcesSync;
import edu.mit.mobile.android.locast.data.Authorable;
import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.data.Locatable;
import edu.mit.mobile.android.locast.data.OrderedList.SyncMapItem;
import edu.mit.mobile.android.locast.data.PrivatelyAuthorable;
import edu.mit.mobile.android.locast.data.SyncMap;
import edu.mit.mobile.android.locast.data.Titled;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

@UriPath(Card.PATH)
@DBSortOrder(Card.SORT_DEFAULT)
public class Card extends JsonSyncableItem implements PrivatelyAuthorable.Columns, Titled.Columns,
        Locatable.Columns {

    public Card(Cursor c) {
        super(c);
    }

    // ///////////////////////////////////////////
    // columns

    public static final int DEFAULT_TIMING = 300;

    /**
     * The amount of time between frames when the card is animated. In millisecond units.
     */
    @DBColumn(type = IntegerColumn.class, notnull = true, defaultValueInt = DEFAULT_TIMING)
    public static final String COL_TIMING = "timing";

    @DBColumn(type = TextColumn.class)
    public static final String COL_ANIMATED_RENDER = "anim_render";

    @DBColumn(type = TextColumn.class)
    public static final String COL_VIDEO_RENDER = "video_render";

    @DBColumn(type = TextColumn.class)
    public static final String COL_VIDEO_RENDER_TYPE = "video_type";

    @DBColumn(type = TextColumn.class)
    public static final String COL_COVER_PHOTO = "cover_photo";

    @DBColumn(type = TextColumn.class)
    public static final String COL_THUMBNAIL = "thumbnail";

    @DBColumn(type = TextColumn.class)
    public static final String COL_MEDIA_URL = "media_url";

    @DBColumn(type = TextColumn.class)
    public static final String COL_WEB_URL = "web_url";

    public static final ForeignKeyManager MEDIA = new ForeignKeyManager(CardMedia.class);

    // ////////////////////////////////////////////////////////

    /**
     * Creates a new card with a random UUID. Cards are marked DRAFT by default.
     *
     * @param cr
     * @param title
     * @return
     */
    public static Uri createNewCard(Context context, Account account, String title) {

        final ContentValues cv = new ContentValues();

        cv.put(Card.COL_TITLE, title);

        return createNewCard(context, account, cv);
    }

    /**
     * Creates a new card with a random UUID. Cards are marked DRAFT by default.
     *
     * @param cr
     * @param cv
     *            initial card contents
     * @return
     */
    public static Uri createNewCard(Context context, Account account, ContentValues cv) {

        JsonSyncableItem.addUuid(cv);

        cv.put(Card.COL_DRAFT, true);

        Authorable.putAuthorInformation(context, account, cv);

        final Uri card = context.getContentResolver().insert(Card.CONTENT_URI, cv);

        return card;
    }

    /**
     * Creates an {@link Intent#ACTION_SEND} intent to share the given card.
     *
     * @param context
     * @param webUrl
     *            the content of the card's {@link Card#COL_WEB_URL} field. Can be a relative URL.
     * @param title
     *            the title of the card
     * @return an intent, within a chooser, that can be used with
     *         {@link Context#startActivity(Intent)}
     */
    public static Intent createShareIntent(Context context, String webUrl, CharSequence title) {
        final NetworkClient nc = LocastApplication.getNetworkClient(context,
                Authenticator.getFirstAccount(context));
        final Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT,
                context.getString(R.string.send_intent_message, nc.getFullUrlAsString(webUrl)));
        sendIntent.putExtra(Intent.EXTRA_SUBJECT,
                context.getString(R.string.send_intent_subject, title));
        return Intent.createChooser(sendIntent,
                context.getString(R.string.send_intent_chooser_title));

    }

    public static CharSequence getTitle(Context context, Cursor c) {
        CharSequence title = c.getString(c.getColumnIndexOrThrow(Card.COL_TITLE));
        if (title == null || title.length() == 0) {
            title = context.getText(R.string.untitled);
        }
        return title;
    }

    public static boolean setCollaborative(ContentResolver cr, Uri card, boolean collaborative) {
        final ContentValues cv = new ContentValues();

        cv.put(Card.COL_PRIVACY, collaborative ? Card.PRIVACY_PUBLIC : Card.PRIVACY_PROTECTED);
        final int count = cr.update(card, cv, null, null);

        return count >= 1;
    }

    public static boolean isCollaborative(Cursor c) {
        return Card.PRIVACY_PUBLIC.equals(c.getString(c.getColumnIndexOrThrow(COL_PRIVACY)));
    }

    public static class CardResources extends AbsResourcesSync {

        @Override
        protected void fromResourcesJSON(Context context, Uri localUri, ContentValues cv,
                JSONObject item) throws NetworkProtocolException, JSONException {
            addToContentValues(cv, "animated_render", item, COL_ANIMATED_RENDER, null, false);
            addToContentValues(cv, "video_render", item, COL_VIDEO_RENDER, COL_VIDEO_RENDER_TYPE,
                    false);
            addToContentValues(cv, "cover_photo", item, COL_COVER_PHOTO, null, false);
            addToContentValues(cv, "thumbnail", item, COL_THUMBNAIL, null, false);

        }
    }

    @Override
    public SyncMap getSyncMap() {
        return SYNC_MAP;
    }

    public static class ItemSyncMap extends JsonSyncableItem.ItemSyncMap {
        /**
         *
         */
        private static final long serialVersionUID = -1061563089458927973L;

        public ItemSyncMap() {
            super();

            putAll(Titled.SYNC_MAP);
            putAll(PrivatelyAuthorable.SYNC_MAP);
            putAll(Locatable.SYNC_MAP);

            put(COL_TIMING, new SyncFieldMap("frame_delay", SyncFieldMap.INTEGER));

            put(COL_WEB_URL, new SyncFieldMap("url", SyncFieldMap.STRING, SyncFieldMap.SYNC_FROM));

            put("_resources", new CardResources());

            put(COL_MEDIA_URL,
                    new SyncChildRelation("photos", new SyncChildRelation.SimpleRelationship(
                            CardMedia.PATH), SyncMapItem.SYNC_FROM));

        }
    };

    public static final ItemSyncMap SYNC_MAP = new ItemSyncMap();

    public static final String SORT_DEFAULT = COL_CREATED_DATE + " DESC";

    public static final String PATH = "card";

    public static final Uri CONTENT_URI = ProviderUtils.toContentUri(CardProvider.AUTHORITY, PATH);

    public static final Uri CONTENT_NOT_DRAFT = CONTENT_URI.buildUpon()
            .appendQueryParameter(Card.COL_DRAFT + "!", "1").build();

    public static final String TYPE_DIR = "vnd.android.cursor.dir/vnd.edu.mit.mobile.android.flipr.card";
    public static final String TYPE_ITEM = "vnd.android.cursor.item/vnd.edu.mit.mobile.android.flipr.card";

    @Override
    public Uri getContentUri() {
        return CONTENT_URI;
    }
}
