package com.gucare.health;

import android.app.AlertDialog;
import android.content.Context;

/** Applies the same elderly-friendly floor to confirmation dialogs and their action buttons. */
final class CareDialogBuilder extends AlertDialog.Builder {
    private final Context context;
    CareDialogBuilder(Context context){super(context,R.style.CarePlatformDialog);this.context=context;}
    @Override public AlertDialog create(){
        AlertDialog dialog=super.create();
        dialog.setOnShowListener(d->{
            Ui ui=new Ui(context,context.getSharedPreferences("prefs",0).getBoolean("large",false));
            if(dialog.getWindow()!=null)ui.enforce(dialog.getWindow().getDecorView());
        });return dialog;
    }
    @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
}
