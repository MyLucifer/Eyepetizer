package com.andyyang.eyepetizer.ui.activity

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.RelativeLayout
import com.andyyang.eyepetizer.R
import com.andyyang.eyepetizer.modle.bean.Issue
import com.andyyang.eyepetizer.modle.bean.Item
import com.andyyang.eyepetizer.presenter.DetailPresenter
import com.andyyang.eyepetizer.showToast
import com.andyyang.eyepetizer.ui.adapter.DetailAdapter
import com.andyyang.eyepetizer.ui.base.BaseActivity
import com.andyyang.eyepetizer.ui.view.detail.*
import com.bumptech.glide.BitmapRequestBuilder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_detail.*
import kotlinx.android.synthetic.main.layout_detail_drop_down.view.*
import java.util.*

/**
 * Created by AndyYang.
 * data: 2018/2/19.
 * mail: AndyyYang2014@126.com.
 */
class DetailActivity : BaseActivity() {

    val presenter by lazy { DetailPresenter(this) }
    lateinit var adapter: DetailAdapter
    private lateinit var itemData: Item
    private val dropDownViews = Stack<DetailDropDownView>()
    private var backgroundBuilder: BitmapRequestBuilder<String, Bitmap>? = null

    override fun getActivityLayoutId() = R.layout.activity_detail

    override fun initActivity(savedInstanceState: Bundle?) {
        initView()
        loadData()
    }

    private fun initView() {
        adapter = DetailAdapter()
        with(rv_detail) {
            layoutManager = LinearLayoutManager(this@DetailActivity)
            adapter = this@DetailActivity.adapter
        }
        initListener()
        with(videoView) {
            isRotateViewAuto = false
            fullscreenButton.setOnClickListener {
                if (this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                startWindowFullscreen(this@DetailActivity, true, true)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        videoView?.fullWindowPlayer?.fullscreenButton?.setOnClickListener {
            GSYVideoPlayer.backFromWindowFull(this)
            if (this.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    private fun initListener() {
        adapter.setOnItemClick({ presenter.requestBasicDataFromMemory(it) },
                { url, title -> dropDownDisPoseable = presenter.requestRelatedAllList(url!!, title!!) },
                {
                    when (it) {
                        BTN_AUTHOR -> showToast("跳到作者")
                        BTN_DOWLOAD -> showToast("下载（未实现）")
                        BTN_REPLY -> presenter.requestReply(itemData.data?.id!!)
                        BTN_FAVORITES -> showToast("喜欢（未实现）")
                        BTN_WATCH -> showToast("加关注（未实现）")
                        BTN_SHARE -> showToast("分享（未实现）")
                    }
                })
    }


    private fun loadData() {
        itemData = intent.getSerializableExtra("data") as Item
        presenter.requestBasicDataFromMemory(itemData)
    }


    override fun onResume() {
        super.onResume()
        GSYVideoManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        GSYVideoManager.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        GSYVideoPlayer.releaseAllVideos()
    }

    fun setPlayer(playUrl: String) {
        videoView.setUp(playUrl, false, "")
        videoView.startPlayLogic()
    }

    fun setMovieAuthorInfo(info: Item) {
        itemData = info
        adapter.addData(info)
    }


    fun setRelated(items: ArrayList<Item>) = adapter.addData(items)

    fun setBackground(url: String) {
        backgroundBuilder = Glide.with(this).load(url).asBitmap()
                .transform(object : BitmapTransformation(this) {
                    override fun getId(): String {
                        return ""
                    }

                    override fun transform(pool: BitmapPool?, toTransform: Bitmap?, outWidth: Int, outHeight: Int): Bitmap {
                        //如果想让图片旋转 非常简单，主要借助于Matrix对象:矩阵对象，将图片转化成像素矩阵。
                        val matrix = Matrix()
                        //执行旋转，参数为旋转角度
                        matrix.postRotate(90f)
                        //将图形的像素点按照这个矩阵进行旋转
                        //将矩阵加载到bitmap对象上，进行输出就可以了  如何创建Bitmap对象
                        //待旋转的bitmap对象，待旋转图片的宽度，待旋转图片的高度
                        return Bitmap.createBitmap(toTransform, 0, 0, toTransform!!.width, toTransform.height, matrix, true)
                    }
                })
                .format(DecodeFormat.PREFER_ARGB_8888).centerCrop()
        backgroundBuilder?.into(background)
    }

    var loadingMore = false

    fun showDropDownView(title: String) {
        val detailDropDownView = DetailDropDownView(this)
        detailDropDownView.title.text = title
        val layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        layoutParams.addRule(RelativeLayout.BELOW, videoView.id)
        detailDropDownView.layoutParams = layoutParams
        backgroundBuilder?.into(detailDropDownView.iv_background)
        root.addView(detailDropDownView)
        dropDownViews.push(detailDropDownView)
        playDropDownAnimation(detailDropDownView, true)
        detailDropDownView.closeButton.setOnClickListener { closeDropDownView() }


        detailDropDownView.rv_detail_more.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val childCount = detailDropDownView.rv_detail_more.childCount
                    val itemCount = detailDropDownView.rv_detail_more.layoutManager.itemCount
                    val firstVisibleItem = (detailDropDownView.rv_detail_more.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                    if (firstVisibleItem + childCount == itemCount) {
                        if (!loadingMore) {
                            loadingMore = true
                            onLoadMore(title)
                        }
                    }
                }
            }
        })

        detailDropDownView.onVideoClick = { item ->
            closeDropDownView()
            presenter.requestBasicDataFromMemory(item)
        }
    }

    //dropdown里请求的数据的disposable，需要在销毁的时候dispose
    private var dropDownDisPoseable: Disposable? = null

    fun onLoadMore(title: String) {
        dropDownDisPoseable = if (title.contains("评论")) {
            presenter.requestMoreReply()
        } else {
            presenter.requestRelatedAllMoreList()
        }
    }

    var dropDownHeight = 0

    private fun playDropDownAnimation(view: DetailDropDownView, show: Boolean) {
        var translateAnimation: Animation
        if ((dropDownHeight == 0)) {
            view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (dropDownHeight != 0) {
                        view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    } else {
                        dropDownHeight = view.height

                        translateAnimation = TranslateAnimation(0f, 0f, dropDownHeight.toFloat(), 0f)
                        translateAnimation.duration = 100
                        view.startAnimation(translateAnimation)
                    }
                }
            })
        } else {
            translateAnimation = if (show) {
                TranslateAnimation(0f, 0f, dropDownHeight.toFloat(), 0f)
            } else {
                TranslateAnimation(0f, 0f, 0f, dropDownHeight.toFloat())
            }
            translateAnimation.duration = 100
            view.startAnimation(translateAnimation)
        }
    }

    private fun closeDropDownView(): Boolean {
        if (dropDownViews.size == 0) {
            return false
        }
        if (dropDownDisPoseable != null && !(dropDownDisPoseable?.isDisposed!!)) {
            dropDownDisPoseable?.dispose()
        }
        playDropDownAnimation(dropDownViews[dropDownViews.size - 1], false)
        root.removeView(dropDownViews[dropDownViews.size - 1])
        dropDownViews.pop()
        return true
    }

    override fun onBackPressed() {
        if (StandardGSYVideoPlayer.backFromWindowFull(this)) {
            if (this.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            return
        }
        val closeMoreList = closeDropDownView()
        if (closeMoreList) {
            return
        }
        super.onBackPressed()
    }

    fun setDropDownView(issue: Issue) {
        val topDropDownView = dropDownViews[dropDownViews.size - 1]
        topDropDownView.setDropDownData(issue.itemList)
    }

    fun setMoreDropDownView(issue: Issue?) {
        loadingMore = false
        val topDropDownView = dropDownViews[dropDownViews.size - 1]
        issue?.let {
            topDropDownView.addDropDownData(issue.itemList)
            return
        }
        //issue为空，没数据
        topDropDownView.addDropDownData(Item("", null, ""))
    }


}