package boaventura.com.devel.br.flutteraudioquery.loaders;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import boaventura.com.devel.br.flutteraudioquery.loaders.tasks.AbstractLoadTask;
import io.flutter.plugin.common.MethodChannel;


public class ArtistLoader extends AbstractLoader {

    private static final String TAG = "MDBG";

    private static final int QUERY_TYPE_DEFAULT = 0x00;
    private static final int QUERY_TYPE_GENRE_ARTISTS = 0x01;
    private static final int QUERY_TYPE_SEARCH_BY_NAME = 0x02;

    private static final String[] PROJECTION = new String [] {
            MediaStore.Audio.AudioColumns._ID, // row id
            MediaStore.Audio.ArtistColumns.ARTIST,
            MediaStore.Audio.ArtistColumns.NUMBER_OF_TRACKS,
            MediaStore.Audio.ArtistColumns.NUMBER_OF_ALBUMS,
    };

    public ArtistLoader(final Context context) { super(context);  }

    /**
     * This method queries in background all artists available in device storage.
     * @param result MethodChannel results
     */
    public void getArtists(final MethodChannel.Result result){
        createLoadTask(result,null,null,
                MediaStore.Audio.Artists.DEFAULT_SORT_ORDER,QUERY_TYPE_DEFAULT).execute();
    }

    public void getArtistsByGenre(final MethodChannel.Result result, final String genreName){

        createLoadTask(result, genreName, null,
                null, QUERY_TYPE_GENRE_ARTISTS ).execute();

    }

    @Override
    protected ArtistLoadTask createLoadTask(
            final MethodChannel.Result result, final String selection,
            final String[] selectionArgs, final String sortOrder, final int type){

        return new ArtistLoadTask(result, getContentResolver(), selection,
                selectionArgs, sortOrder,type);
    }

    static class ArtistLoadTask extends AbstractLoadTask<List <Map<String,Object>> > {
        private ContentResolver m_resolver;
        private MethodChannel.Result m_result;
        private int m_queryType;


        ArtistLoadTask(final MethodChannel.Result result, final ContentResolver resolver, final String selection,
                       final String[] selectionArgs, final String sortOrder, final int type){
            super(selection, selectionArgs, sortOrder);

            m_resolver = resolver;
            m_result = result;
            m_queryType = type;
        }

        @Override
        protected void onPostExecute(List<Map<String, Object>> maps) {
            super.onPostExecute(maps);
            m_result.success(maps);
            m_result = null;
            m_resolver = null;
        }

        @Override
        protected List< Map<String,Object> > loadData(final String selection,
                                                    final String [] selectionArgs, final String sortOrder){


            switch (m_queryType){
                case ArtistLoader.QUERY_TYPE_DEFAULT:
                    return basicDataLoad(selection,selectionArgs,sortOrder);


                case ArtistLoader.QUERY_TYPE_GENRE_ARTISTS:
                    /// in this case the genre name comes from selection param
                    List<String> artistsName = loadArtistNamesByGenre(selection);
                    int totalArtists = artistsName.size();
                    if (totalArtists > 0){
                        if (totalArtists > 1){
                            String[] params = artistsName.toArray(new String[artistsName.size()]);

                            String createdSelection = createMultipleValueSelectionArgs(params );

                            return basicDataLoad( createdSelection, params,
                                    MediaStore.Audio.Artists.DEFAULT_SORT_ORDER);
                        }

                        else {
                            return basicDataLoad(
                                    MediaStore.Audio.Artists.ARTIST + " =?",
                                    new String[] { artistsName.get(0)},
                                    MediaStore.Audio.Artists.DEFAULT_SORT_ORDER);
                        }
                    }
                    return new ArrayList<>();
            }

            Cursor artistCursor = m_resolver.query(
                    MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                    ArtistLoader.PROJECTION,
                    selection, selectionArgs, sortOrder );

            List< Map<String,Object> > list = new ArrayList<>();
            if (artistCursor != null){

                while ( artistCursor.moveToNext() ){
                    Map<String, Object> map = new HashMap<>();
                    for (String artistColumn : PROJECTION) {
                        String data = artistCursor.getString(artistCursor.getColumnIndex(artistColumn));
                        map.put(artistColumn, data);
                    }
                    // some album artwork of this artist that can be used
                    // as artist cover picture if there is one.
                    map.put("artist_cover", getArtistArtPath( (String) map.get(PROJECTION[1]) ) );
                    list.add( map );
                }
                artistCursor.close();
            }

            return list;
        }

        private List< Map<String,Object> > basicDataLoad(
                final String selection, final String [] selectionArgs, final String sortOrder){
            Cursor artistCursor = m_resolver.query(
                    MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                    ArtistLoader.PROJECTION,
                    /*where clause*/selection,
                    /*where clause arguments */selectionArgs,
                    sortOrder );

            List< Map<String,Object> > list = new ArrayList<>();
            if (artistCursor != null){

                while ( artistCursor.moveToNext() ){
                    Map<String, Object> map = new HashMap<>();
                    for (String artistColumn : PROJECTION) {
                        String data = artistCursor.getString(artistCursor.getColumnIndex(artistColumn));
                        map.put(artistColumn, data);
                    }
                    // some album artwork of this artist that can be used
                    // as artist cover picture if there is one.
                    map.put("artist_cover", getArtistArtPath( (String) map.get(PROJECTION[1]) ) );
                    //Log.i("MDGB", "getting: " +  (String) map.get(MediaStore.Audio.Media.ARTIST));
                    list.add( map );
                }
                artistCursor.close();
            }

            return list;
        }

        /**
         * Method used to get some album artwork image path from an especifc artist
         * and this image can be used as artist cover.
         * @param artistName name of artist
         * @return Path String from some album from artist or null if there is no one.
         */
        private String getArtistArtPath(String artistName){
            String artworkPath = null;

            Cursor artworkCursor = m_resolver.query(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    new String[] {
                            MediaStore.Audio.AlbumColumns.ALBUM_ART,
                            MediaStore.Audio.AlbumColumns.ARTIST
                    },

                    MediaStore.Audio.AlbumColumns.ARTIST + "=?",
                    new String[] {artistName},
                    MediaStore.Audio.Albums.DEFAULT_SORT_ORDER );

            if ( artworkCursor != null ){
                //Log.i(TAG, "total paths " + artworkCursor.getCount());

                while (artworkCursor.moveToNext()){
                    artworkPath = artworkCursor.getString(
                            artworkCursor.getColumnIndex( MediaStore.Audio.Albums.ALBUM_ART)
                    );

                    // breaks in first valid path founded.
                    if (artworkPath !=null )
                        break;
                }

                //Log.i(TAG, "found path: " + artworkPath );
                artworkCursor.close();
            }

            return artworkPath;
        }

        private List<String> loadArtistNamesByGenre(final String genreName){
            //Log.i("MDBG",  "Genero: " + genreName +" Artistas: ");
            List<String> artistsIds = new ArrayList<>();

            Cursor artistNamesCursor = m_resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{"Distinct " + MediaStore.Audio.Media.ARTIST, "genre_name" },
                    "genre_name" + " =?",new String[] {genreName},null);

            if (artistNamesCursor != null){
                //Log.i("MDBG", "TOTAL DE RESLTADOS ARTIST-> GENERO: " + artistNamesCursor.getCount());

                while (artistNamesCursor.moveToNext()){
                    String artistName = artistNamesCursor.getString( artistNamesCursor.getColumnIndex(
                            MediaStore.Audio.Media.ARTIST ));

                    //Log.i("MDBG",  artistName);
                    artistsIds.add(artistName);
                }
                //Log.i("MDBG",  "-----------");
                artistNamesCursor.close();
            }

            return artistsIds;
        }

        /**
         * This method creates a string for sql queries that has
         * multiple values for column MediaStore.Audio.Artist.artist.
         *
         * something like:
         * SELECT column1, column2, columnN FROM ARTIST Where id in (1,2,3,4,5,6);
         * @param params
         * @return
         */
        private String createMultipleValueSelectionArgs( /*String column */String[] params){

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(MediaStore.Audio.Artists.ARTIST + " IN(?" );

            for(int i=0; i < (params.length-1); i++)
                stringBuilder.append(",?");

            stringBuilder.append(')');
            return stringBuilder.toString();
        }


    }
}