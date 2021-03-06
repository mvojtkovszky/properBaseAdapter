package com.vojtkovszky.properbaseadapter

import android.os.Handler
import android.os.Looper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Created by Marcel Vojtkovszky on 2020/05/09.
 *
 * Convenient interface to use the library in the most straight forward way by implementing it
 * in a View, Fragment or Activity.
 *
 * We can, however, choose not to use it and therefore and use ProperBaseAdapter in full manual mode.
 * In that case we have to manually construct the adapter and data, set it to RecyclerView, define
 * LayoutManager and all that.
 */
interface ProperBaseAdapterImplementation {

    /**
     * Retrieve an adapter. Will only exist once RecyclerView is set-up and populated, which
     * happens after [refreshRecyclerView] is called
     */
    fun getAdapter(): ProperBaseAdapter? {
        return if (adapterExistsAndSet()) getRecyclerView()?.adapter as ProperBaseAdapter else null
    }

    /**
     * Provide list of Adapter Items. Will invoke after [refreshRecyclerView] gets called.
     * Also an opportunity to tweak adapter itself before data in it gets refreshed.
     *
     * @param adapter instance of [ProperBaseAdapter] before changes are applied to it. Gives us a
     * chance to change its behaviour before changes are visible on [RecyclerView] itself.
     * @param data convenience parameter - empty mutable list of adapter items to which adapter
     * items can be added and returned.
     * @return list of adapter items. Can be [data]
     */
    fun getAdapterData(adapter: ProperBaseAdapter,
                       data: MutableList<AdapterItem<*>> = mutableListOf()
    ): MutableList<AdapterItem<*>>

    /**
     * Define a layout manager.
     * Default implementation will use [LinearLayoutManager]
     */
    fun getNewLayoutManager(): RecyclerView.LayoutManager? {
        return getRecyclerView()?.let { LinearLayoutManager(it.context) }
    }

    /**
     * Define a reference to [RecyclerView] in your view.
     * Allows it to be null (common in case views are not yet set) - in this case nothing will happen
     * as null RecyclerView cannot be refreshed, thus calling [refreshRecyclerView] will do nothing.
     */
    fun getRecyclerView(): RecyclerView?

    /**
     * Optionally override this method and provide result based on the lifecycle status.
     *
     * Every time RecyclerView is about to be populated, we decide whether we're allowed to continue
     * based on this method.
     *
     * This allows to omit repetitive code similar to
     * 'if (!activity.isDestroyed && !activity.isFinishing) refreshRecyclerView()'
     * to cover edge cases where [refreshRecyclerView] might be called while lifecycle within the
     * used context is in state of becoming invalid, potentially causing a crash.
     * Example where this might happen is when using a lot of delays or triggering data refresh
     * from an event originating in a background thread.
     *
     * By default it will always be true (considered valid).
     */
    fun isLifecycleValid(): Boolean = true

    /**
     * This method will be called every time [refreshRecyclerView] completes.
     * Override is optional.
     */
    fun onRecyclerViewRefreshed() {
        return
    }

    /**
     * Trigger recycler view refresh with data provided in [getAdapterData].
     * In order to see changes, RecyclerView should not be null at this point.
     *
     * @param refreshType see [DataDispatchMethod]
     * @param waitUntilRecyclerViewLaidDown determine if we should wait until RecyclerView is laid down
     * before refreshing data by calling [RecyclerView.post]
     * @param delayMillis refresh with a delay, in ms
     */
    fun refreshRecyclerView(refreshType: DataDispatchMethod = DataDispatchMethod.DISPATCH_ONLY_CHANGES,
                            waitUntilRecyclerViewLaidDown: Boolean = false,
                            delayMillis: Long? = null) {
        // check if explicitly reported out of lifecycle
        if (!isLifecycleValid()) {
            return
        }

        // handle delay
        if (delayMillis != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                refreshRecyclerView(
                    refreshType = refreshType,
                    waitUntilRecyclerViewLaidDown = false,
                    delayMillis = null)
            }, delayMillis)
            return
        }

        // trigger populate, either directly or wait until recycler view laid down
        getRecyclerView()?.let {
            if (waitUntilRecyclerViewLaidDown) {
                it.post { setupAndPopulateRecyclerView(it, refreshType) }
            } else {
                setupAndPopulateRecyclerView(it, refreshType)
            }
        }
    }

    /**
     * Define sticky header behaviour when one sticky header is in the process of replacing another.
     * Defaults to [Boolean.false]. If set to [Boolean.true], it will apply a fade effect.
     * Only applies if adapter contains at least one item marked as sticky header to begin with.
     */
    fun fadeOutStickyHeaders(): Boolean {
        return false
    }

    // determine if current recycler view has adapter set and this adapter is ProperBaseAdapter
    private fun adapterExistsAndSet(): Boolean {
        return getRecyclerView()?.adapter != null && getRecyclerView()?.adapter is ProperBaseAdapter
    }

    // setup adapter to recycler view and populate it
    private fun setupAndPopulateRecyclerView(recyclerView: RecyclerView, refreshType: DataDispatchMethod) {
        // check if explicitly reported out of lifecycle
        if (!isLifecycleValid()) {
            return
        }

        // set layout manager if not set
        if (recyclerView.layoutManager == null) {
            recyclerView.layoutManager = getNewLayoutManager()
        }

        // setup adapters
        val adapter = getAdapter() ?: ProperBaseAdapter().also {
            // allows us to properly set default layout parameters in case LinearLayout is used
            if (recyclerView.layoutManager is LinearLayoutManager) {
                it.linearLayoutManagerOrientation = (recyclerView.layoutManager as LinearLayoutManager).orientation
            }
            // make this a default restoration state policy
            it.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }

        // different behaviour based on refresh type
        when (refreshType) {
            DataDispatchMethod.DISPATCH_ONLY_CHANGES -> adapter.updateItems(getAdapterData(adapter))
            DataDispatchMethod.SET_DATA_AND_REFRESH -> adapter.setItems(getAdapterData(adapter), true)
            DataDispatchMethod.SET_DATA_ONLY -> adapter.setItems(getAdapterData(adapter), false)
        }

        // add support for sticky headers if at least one item supports it
        if (adapter.hasStickyHeaders() && recyclerView.itemDecorationCount == 0) {
            recyclerView.addItemDecoration(StickyHeaderItemDecoration(recyclerView, fadeOutStickyHeaders()) {
                adapter.getItemAt(it)?.isStickyHeader == true
            })
        }

        // set adapter to recycler view if not set
        if (recyclerView.adapter == null) {
            recyclerView.adapter = adapter
        }

        // all done, call refreshed method
        onRecyclerViewRefreshed()
    }
}