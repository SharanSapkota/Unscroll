package com.unscroll.app.data.permission

import app.cash.turbine.test
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.permission.PermissionState
import com.unscroll.app.testing.FakePermissionChecker
import com.unscroll.app.testing.permissions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionRepositoryTest {

    private val checker = FakePermissionChecker(PermissionState.NONE)

    @Test
    fun initialState_isCheckedImmediately() {
        checker.state = permissions(AppPermission.OVERLAY)
        val repository = PermissionRepository(checker)

        assertEquals(permissions(AppPermission.OVERLAY), repository.permissions.value)
    }

    @Test
    fun refresh_emitsNewStateAfterUserReturnsFromSettings() = runTest {
        val repository = PermissionRepository(checker)

        repository.permissions.test {
            assertEquals(PermissionState.NONE, awaitItem())

            checker.state = permissions(AppPermission.USAGE_ACCESS)
            repository.refresh()

            assertEquals(permissions(AppPermission.USAGE_ACCESS), awaitItem())
        }
    }

    @Test
    fun refresh_withoutChange_doesNotEmit() = runTest {
        val repository = PermissionRepository(checker)

        repository.permissions.test {
            assertEquals(PermissionState.NONE, awaitItem())
            repository.refresh()
            expectNoEvents()
        }
    }

    @Test
    fun isGranted_onlyEmitsWhenThatPermissionChanges() = runTest {
        val repository = PermissionRepository(checker)

        repository.isGranted(AppPermission.OVERLAY).test {
            assertEquals(false, awaitItem())

            checker.state = permissions(AppPermission.USAGE_ACCESS)
            repository.refresh()
            expectNoEvents()

            checker.state = permissions(AppPermission.USAGE_ACCESS, AppPermission.OVERLAY)
            repository.refresh()
            assertEquals(true, awaitItem())

            checker.state = permissions(AppPermission.USAGE_ACCESS)
            repository.refresh()
            assertEquals(false, awaitItem())
        }
    }
}
