package org.odk.collect.android.formentry.audit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.odk.collect.android.javarosawrapper.FormController;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class IdentityPromptViewModelTest {

    @Test
    public void done_setsUserOnAuditEventLogger() throws IOException {
        FormController formController = mock(FormController.class);
        AuditEventLogger auditEventLogger = mock(AuditEventLogger.class);
        when(formController.getAuditEventLogger()).thenReturn(auditEventLogger);
        when(formController.getInstanceFile()).thenReturn(File.createTempFile("instance", ".xml"));

        IdentityPromptViewModel viewModel = new IdentityPromptViewModel();

        viewModel.formLoaded(formController);
        viewModel.setIdentity("Picard");
        viewModel.done();
        verify(auditEventLogger).setUser("Picard");
    }
}