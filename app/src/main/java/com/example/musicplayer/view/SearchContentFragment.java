package com.example.musicplayer.view;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.musicplayer.R;
import com.example.musicplayer.adapter.SearchContentAdapter;
import com.example.musicplayer.constant.BroadcastName;
import com.example.musicplayer.constant.Constant;
import com.example.musicplayer.contract.ISearchContentContract;
import com.example.musicplayer.entiy.Album;
import com.example.musicplayer.entiy.SeachSong;
import com.example.musicplayer.entiy.Song;
import com.example.musicplayer.presenter.SearchContentPresenter;
import com.example.musicplayer.service.PlayerService;
import com.example.musicplayer.util.CommonUtil;
import com.example.musicplayer.util.FileHelper;
import com.github.jdsjlzx.interfaces.OnLoadMoreListener;
import com.github.jdsjlzx.interfaces.OnNetWorkErrorListener;
import com.github.jdsjlzx.recyclerview.LRecyclerView;
import com.github.jdsjlzx.recyclerview.LRecyclerViewAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 残渊 on 2018/11/21.
 */

public class SearchContentFragment extends Fragment implements ISearchContentContract.View {
    private static final String TAG = "SearchContentFragment";
    public static final String TYPE_KEY = "type";
    public static final String SEEK_KEY = "seek";
    public static final String IS_ONLINE = "online";
    private int mOffset = 1; //用于翻页搜索

    private String mAlbumName,mSingerName,mAlbumPic,mPublicTime;

    private SearchContentPresenter mPresenter;
    private LRecyclerView mRecycler;
    private LinearLayoutManager manager;
    private SearchContentAdapter mAdapter;
    private ArrayList<SeachSong.DataBean> mSongList = new ArrayList<>();
    private List<Album.DataBean> mAlbumList;
    private IntentFilter intentFilter;

    private LRecyclerViewAdapter mLRecyclerViewAdapter;//下拉刷新

    private Bundle mBundle;
    private String mSeek;
    private String mType;

    private PlayerService.PlayStatusBinder mPlayStatusBinder;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mPlayStatusBinder = (PlayerService.PlayStatusBinder) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search_content, container, false);
        mBundle = getArguments();
        if (mBundle != null) {
            mSeek = mBundle.getString(SEEK_KEY);
            mType = mBundle.getString(TYPE_KEY);
        }

        mRecycler = view.findViewById(R.id.recycler_song_list);
        mPresenter = new SearchContentPresenter();
        mPresenter.attachView(this);
        if (mType.equals("song")) {
            mPresenter.search(mSeek, 1);
        } else if (mType.equals("album")) {
            mPresenter.searchAlbum(mSeek, 1);
            mRecycler.setBackgroundResource(R.color.translucent);
        }
        searchMore();
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        manager = new LinearLayoutManager(getActivity());

        //注册广播
        intentFilter = new IntentFilter();
        intentFilter.addAction(BroadcastName.ONLINE_SONG_FINISH);
        intentFilter.addAction(BroadcastName.ONLINE_ALBUM_SONG_Change);
        SongFinishReceiver songFinishReceiver = new SongFinishReceiver();
        getActivity().registerReceiver(songFinishReceiver, intentFilter);

        //启动服务
        Intent playIntent = new Intent(getActivity(), PlayerService.class);
        getActivity().bindService(playIntent, connection, Context.BIND_AUTO_CREATE);


    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        getActivity().unbindService(connection);
    }


    @Override
    public void setSongsList(final ArrayList<SeachSong.DataBean> songListBeans) {
        mSongList.addAll(songListBeans);
        mAdapter = new SearchContentAdapter(mSongList, mSeek, getActivity(), Constant.TYPE_SONG);
        mLRecyclerViewAdapter = new LRecyclerViewAdapter(mAdapter);
        mRecycler.setLayoutManager(manager);
        mRecycler.setAdapter(mLRecyclerViewAdapter);


        mAdapter.setItemClick(new SearchContentAdapter.ItemClick() {
            @Override
            public void onClick(int position) {
                SeachSong.DataBean dataBean = mSongList.get(position);
                Song song = new Song();
                song.setOnlineId(dataBean.getId());
                song.setSinger(dataBean.getSinger());
                song.setSongName(dataBean.getName());
                song.setUrl(dataBean.getUrl());
                song.setImgUrl(dataBean.getPic());
                song.setCurrent(position);
                song.setOnline(true);
                FileHelper.saveSong(song);

                mPlayStatusBinder.playOnline();
            }
        });
    }

    @Override
    public void searchMoreSuccess(ArrayList<SeachSong.DataBean> songListBeans) {
        Log.d(TAG, "searchMoreSuccess: success=" + songListBeans.size());
        mSongList.addAll(songListBeans);
        mAdapter.notifyDataSetChanged();
        mRecycler.refreshComplete(Constant.OFFSET);
    }

    @Override
    public void searchMoreError() {
        mRecycler.setNoMore(true);
    }

    @Override
    public void searchMore() {

        mRecycler.setPullRefreshEnabled(false);
        mRecycler.setOnLoadMoreListener(new OnLoadMoreListener() {
            @Override
            public void onLoadMore() {
                mOffset += 1;
                Log.d(TAG, "onLoadMore: mOffset=" + mOffset);
                if (mType.equals("song")) {
                    mPresenter.searchMore(mSeek, mOffset);
                }else{
                    mPresenter.searchAlbumMore(mSeek,mOffset);
                }
            }
        });
        //设置底部加载颜色
        mRecycler.setFooterViewColor(R.color.colorAccent, R.color.musicStyle_low, R.color.translucent);
        //设置底部加载文字提示
        mRecycler.setFooterViewHint("拼命加载中", "已经全部为你呈现了", "网络不给力啊，点击再试一次吧");

    }

    @Override
    public void showError() {
        CommonUtil.showToast(getActivity(), "连接超时");
    }

    @Override
    public void showSearcherMoreNetworkError() {
        mRecycler.setOnNetWorkErrorListener(new OnNetWorkErrorListener() {
            @Override
            public void reload() {
                mOffset += 1;
                mPresenter.searchMore(mSeek,  mOffset);
            }
        });
    }

    @Override
    public void searchAlbumSuccess(final List<Album.DataBean> albumList) {
        mAlbumList = new ArrayList<>();
        mAlbumList.addAll(albumList);
        mAdapter = new SearchContentAdapter(mAlbumList, mSeek, getActivity(), Constant.TYPE_ALBUM);
        mLRecyclerViewAdapter = new LRecyclerViewAdapter(mAdapter);
        mRecycler.setLayoutManager(manager);
        mRecycler.setAdapter(mLRecyclerViewAdapter);

        mAdapter.setAlbumClick(new SearchContentAdapter.AlbumClick() {
            @Override
            public void onClick(int position) {
                toAlbumContentFragment(mAlbumList.get(position));
            }
        });
    }

    @Override
    public void searchAlbumMoreSuccess(List<Album.DataBean> songListBeans) {
        mAlbumList.addAll(songListBeans);
        mAdapter.notifyDataSetChanged();
        mRecycler.refreshComplete(Constant.OFFSET);
    }

    @Override
    public void searchAlbumError() {
        CommonUtil.showToast(getActivity(), "获取专辑信息失败");
    }

    /**
     * 构造带参数的fragment
     *
     * @param type 参数
     */
    public static Fragment newInstance(String seek, String type) {
        SearchContentFragment fragment = new SearchContentFragment();
        Bundle bundle = new Bundle();
        bundle.putString(TYPE_KEY, type);
        bundle.putString(SEEK_KEY, seek);
        fragment.setArguments(bundle);
        return fragment;
    }

    class SongFinishReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: "+intent.getAction());
            mAdapter.notifyDataSetChanged();
        }
    }

    public void toAlbumContentFragment(Album.DataBean album){
        FragmentManager manager = getActivity().getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();
        transaction.setCustomAnimations(R.anim.fragment_in, R.anim.fragment_out, R.anim.slide_in_right, R.anim.slide_out_right);
        transaction.add(R.id.fragment_container,AlbumContentFragment.
                newInstance(album.getAlbumMID(),album.getAlbumName(),album.getAlbumPic(),album.getSingerName(),album.getPublicTime()));
        transaction.hide(this);
        //将事务提交到返回栈
        transaction.addToBackStack(null);
        transaction.commit();
    }
}