package com.pyrus.pyrusservicedesk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.MainThread
import com.google.gson.GsonBuilder
import com.pyrus.pyrusservicedesk.presentation.ui.navigation_page.ticket.TicketActivity
import com.pyrus.pyrusservicedesk.presentation.ui.navigation_page.tickets.TicketsActivity
import com.pyrus.pyrusservicedesk.presentation.viewmodel.QuitViewModel
import com.pyrus.pyrusservicedesk.sdk.FileResolverImpl
import com.pyrus.pyrusservicedesk.sdk.RequestFactory
import com.pyrus.pyrusservicedesk.sdk.data.LocalDataProvider
import com.pyrus.pyrusservicedesk.sdk.data.gson.RemoteGsonExclusionStrategy
import com.pyrus.pyrusservicedesk.sdk.data.gson.UriGsonAdapter
import com.pyrus.pyrusservicedesk.sdk.repositories.draft.DraftRepository
import com.pyrus.pyrusservicedesk.sdk.repositories.draft.PreferenceDraftRepository
import com.pyrus.pyrusservicedesk.sdk.repositories.general.CentralRepository
import com.pyrus.pyrusservicedesk.sdk.repositories.offline.OfflineRepository
import com.pyrus.pyrusservicedesk.sdk.repositories.offline.PreferenceOfflineRepository
import com.pyrus.pyrusservicedesk.sdk.response.ResponseCallback
import com.pyrus.pyrusservicedesk.sdk.response.ResponseError
import com.pyrus.pyrusservicedesk.sdk.updates.LiveUpdates
import com.pyrus.pyrusservicedesk.sdk.updates.NewReplySubscriber
import com.pyrus.pyrusservicedesk.sdk.verify.LocalDataVerifier
import com.pyrus.pyrusservicedesk.sdk.verify.LocalDataVerifierImpl
import com.pyrus.pyrusservicedesk.sdk.web.retrofit.RetrofitWebRepository
import com.pyrus.pyrusservicedesk.utils.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class PyrusServiceDesk private constructor(
        internal val application: Application,
        internal val appId: String,
        internal val isSingleChat: Boolean){

    companion object {
        internal val DISPATCHER_IO_SINGLE = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        internal var FILE_CHOOSER: FileChooser? = null
        private var INSTANCE: PyrusServiceDesk? = null
        private var CONFIGURATION: ServiceDeskConfiguration? = null

        /**
         * Initializes PyrusServiceDesk embeddable module.
         * The best approach is to call this in [Application.onCreate]
         * ***PS***: Should be done before other public methods are is called.
         * Unhandled IllegalStateException is thrown otherwise.
         *
         * @param application instance of the enclosing application
         * @param appId id of a client
         */
        @JvmStatic
        fun init(application: Application, appId: String) {
            INSTANCE = PyrusServiceDesk(application, appId, true)
        }

        /**
         * Launches UI of the PyrusServiceDesk with default configuration.
         *
         * @param activity activity that is used for launching service desk UI
         */
        @JvmStatic
        fun start(activity: Activity) {
            startImpl(activity = activity)
        }

        /**
         * Launches UI of the PyrusServiceDesk.
         *
         * @param activity activity that is used for launching service desk UI
         * @param configuration instance of [ServiceDeskConfiguration]. This is used for customizing UI
         */
        @JvmStatic
        fun start(activity: Activity, configuration: ServiceDeskConfiguration) {
            startImpl(activity = activity, configuration = configuration)
        }

        /**
         * Registers [subscriber] that will be notified when new replies from support are received
         */
        @JvmStatic
        @MainThread
        fun subscribeToReplies(subscriber: NewReplySubscriber){
            get().liveUpdates.subscribeOnReply(subscriber)
        }

        /**
         * Unregisters [subscriber] from updates of new reply from support
         */
        @JvmStatic
        @MainThread
        fun unsubscribeFromReplies(subscriber: NewReplySubscriber) {
            get().liveUpdates.unsubscribeFromReplies(subscriber)
        }

        /**
         * Assigns custom file chooser, that is appended as variant when the user is offered to choose the source
         * to attach a file to comment from.
         * Files which size exceed [RequestUtils.MAX_FILE_SIZE_MEGABYTES] MB will be ignored.
         *
         * @param fileChooser FileChooser instance that is used for launching custom UI for picking files.
         *                  null can be passed to unregister custom chooser.
         */
        @JvmStatic
        fun registerFileChooser(fileChooser: FileChooser) {
            FILE_CHOOSER = fileChooser
        }

        /**
         * Launches the request for registering push token.
         * Callback can be invoked in a thread that differs from the one that has invoked [setPushToken]
         *
         * @param token string token to be registered
         * @param callback callback that is invoked when result of registering of the token is received.
         *  This is invoked without error when token is successfully registered.
         */
        @JvmStatic
        fun setPushToken(token: String, callback: SetPushTokenCallback) {
            val serviceDesk = get()
            when{
                token.isBlank() -> callback.onResult(Exception("Token is empty"))
                serviceDesk.appId.isBlank() -> callback.onResult(Exception("AppId is not assigned"))
                serviceDesk.userId.isBlank() -> callback.onResult(Exception("UserId is not assigned"))
                else -> {
                    GlobalScope.launch {
                        serviceDesk
                            .requestFactory
                            .getSetPushTokenRequest(token)
                            .execute(object: ResponseCallback<Unit>{
                                override fun onSuccess(data: Unit) {
                                    callback.onResult(null)
                                }
                                override fun onFailure(responseError: ResponseError) {
                                    callback.onResult(responseError)
                                }
                            })
                    }
                }
            }
        }

        internal fun get() : PyrusServiceDesk {
            return checkNotNull(INSTANCE){ "Instantiate PyrusServiceDesk first" }
        }

        internal fun getConfiguration(): ServiceDeskConfiguration {
            if (CONFIGURATION == null)
                CONFIGURATION = ServiceDeskConfiguration()
            return CONFIGURATION!!
        }

        internal fun setConfiguration(config: ServiceDeskConfiguration) {
            CONFIGURATION = config
        }

        private fun startImpl(ticketId: Int? = null, activity: Activity, configuration: ServiceDeskConfiguration? = null) {
            CONFIGURATION = configuration
            activity.startActivity(createIntent(ticketId))
        }

        private fun createIntent(ticketId: Int? = null): Intent {
            return when{
                ticketId != null -> TicketActivity.getLaunchIntent(ticketId)
                PyrusServiceDesk.get().isSingleChat -> TicketActivity.getLaunchIntent()
                else -> TicketsActivity.getLaunchIntent()
            }
        }
    }

    internal val serviceDeskProvider: ServiceDeskProvider by lazy {
        object : ServiceDeskProvider{
            override fun getApplication(): Application = application
            override fun getRequestFactory(): RequestFactory = requestFactory
            override fun getDraftRepository(): DraftRepository = draftRepository
            override fun getLiveUpdates(): LiveUpdates = liveUpdates
            override fun getLocalDataProvider(): LocalDataProvider = localDataProvider
            override fun getLocalDataVerifier(): LocalDataVerifier = localDataVerifier
        }
    }

    internal var userId: String

    private val requestFactory: RequestFactory
    private val draftRepository: DraftRepository
    private val liveUpdates: LiveUpdates

    private val localDataProvider: LocalDataProvider by lazy { LocalDataProvider(offlineRepository, fileResolver) }
    private val localDataVerifier: LocalDataVerifier

    private var quitViewModel = QuitViewModel()

    private val fileResolver: FileResolverImpl = FileResolverImpl(application.contentResolver)
    private val preferences = application.getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE)
    private val offlineRepository: OfflineRepository

    init {
        migratePreferences(application, preferences)

        userId = ConfigUtils.getUserId(preferences)

        localDataVerifier = LocalDataVerifierImpl(fileResolver)

        val offlineGson =
            GsonBuilder()
                .setDateFormat(ISO_DATE_PATTERN)
                .registerTypeAdapter(Uri::class.java, UriGsonAdapter())
                .create()
        offlineRepository = PreferenceOfflineRepository(preferences, localDataVerifier, offlineGson)

        val remoteGson =
            GsonBuilder()
                .setDateFormat(ISO_DATE_PATTERN)
                .addSerializationExclusionStrategy(RemoteGsonExclusionStrategy())
                .create()

        val centralRepository = CentralRepository(
            RetrofitWebRepository(appId, userId, fileResolver, remoteGson),
            offlineRepository
        )

        requestFactory = RequestFactory(centralRepository)
        draftRepository = PreferenceDraftRepository(preferences)
        liveUpdates = LiveUpdates(requestFactory)
    }

    internal fun getSharedViewModel() = quitViewModel
}