package alexcmb.mytvlauncher.widget

import alexcmb.mytvlauncher.LauncherActivity
import alexcmb.mytvlauncher.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment

/** The widgets page of the browse: the band, on its own, with room to breathe. */
class WidgetsPageFragment : Fragment(), BrowseSupportFragment.MainFragmentAdapterProvider {

    private val fragmentAdapter = BrowseSupportFragment.MainFragmentAdapter(this)

    override fun getMainFragmentAdapter(): BrowseSupportFragment.MainFragmentAdapter<*> =
        fragmentAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_widgets, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The controller lives on the activity, which the bind dialogs need; the band it
        // draws into belongs to this page.
        (requireActivity() as LauncherActivity).widgetSlot.attachBand(
            view.findViewById<LinearLayout>(R.id.widget_band),
            view.findViewById(R.id.widget_empty)
        )
        // Without this the browse keeps waiting on us and won't hand over the focus.
        fragmentAdapter.fragmentHost?.notifyDataReady(fragmentAdapter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (requireActivity() as LauncherActivity).widgetSlot.detachBand()
    }
}
