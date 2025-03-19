package jp.datasign.bunsin_wallet.viewModel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import jp.datasign.bunsin_wallet.datastore.CredentialDataStore
import jp.datasign.bunsin_wallet.ui.certificate.CertificateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class CertificateViewModelTest {

    // テスト対象のViewModel
    private lateinit var viewModel: CertificateViewModel
    private val credentialDataStore = mock(CredentialDataStore::class.java)
    private val testCredentialDataList = jp.datasign.bunsin_wallet.datastore.CredentialDataList.getDefaultInstance() // テストデータ

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // CredentialDataStore のモックの設定
        `when`(credentialDataStore.credentialDataListFlow).thenReturn(flowOf(testCredentialDataList))

        viewModel = CertificateViewModel(credentialDataStore)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testCredentialDataList() = runTest {
        val observer = mock(Observer::class.java) as Observer<jp.datasign.bunsin_wallet.datastore.CredentialDataList?>
        viewModel.credentialDataList.observeForever(observer)

        // LiveDataが更新されたことを検証
        verify(observer).onChanged(testCredentialDataList)

        // LiveDataの現在値を検証
        assertEquals(testCredentialDataList, viewModel.credentialDataList.value)
    }
}