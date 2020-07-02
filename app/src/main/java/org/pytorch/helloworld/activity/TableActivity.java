package org.pytorch.helloworld.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.pytorch.helloworld.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;


public class TableActivity extends AppCompatActivity {
    TableLayout mTableLayout;
    String[][] tableData;
    private String filepath = "Documents";
    private Random random;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        random = new Random();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_table);
        mTableLayout = findViewById(R.id.table);

        tableData = null;
        try {
            Object[] objectArray = (Object[]) getIntent().getExtras().getSerializable("TABLE DATA");
            tableData = new String[objectArray.length][];
            for (int i = 0; i < objectArray.length; i++) {
                tableData[i] = (String[]) objectArray[i];
            }
        } catch (NullPointerException e) {
            Toast.makeText(this, "There is no data available", Toast.LENGTH_SHORT).show();
        }

        assert tableData != null;
        for (int i = 0; i < tableData.length; i++) {
            TableRow row = new TableRow(this);
            row.setId(View.generateViewId());
            row.setLayoutParams(new TableLayout.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT
            ));

            EditText editText1 = new EditText(this);
            editText1.setId(View.generateViewId());
            editText1.setText(tableData[i][0]);
            editText1.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            row.addView(editText1);

            EditText editText2 = new EditText(this);
            editText2.setId(View.generateViewId());
            editText2.setText(tableData[i][1]);
            editText2.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            row.addView(editText2);

            EditText editText4 = new EditText(this);
            editText4.setId(View.generateViewId());
            editText4.setText(tableData[i][4]);
            editText4.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            row.addView(editText4);

            mTableLayout.addView(row);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.share, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.share_action) {
            shareExcelFile();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void shareExcelFile() {

        Intent intentShareFile = new Intent(Intent.ACTION_SEND);
        String tempFile = createExcelFile();
        if (tempFile == null) {
            Toast.makeText(this, "Cannot make file", Toast.LENGTH_SHORT).show();
        }
        File shareFile = new File(Environment.getExternalStorageDirectory(), tempFile);
        intentShareFile.setType("image/*");
        intentShareFile.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(shareFile));
        startActivity(Intent.createChooser(intentShareFile, "Share excel file"));
//        boolean del = shareFile.delete();
    }

    private String createExcelFile() {
        if (tableData == null) {
            Toast.makeText(this, "There is no data available yet", Toast.LENGTH_SHORT).show();
            return null;
        }
        Workbook wb = new HSSFWorkbook();
        Cell c;
        Sheet sheet = wb.createSheet();
        int idx = 0;
        Row row;
        row = sheet.createRow(idx);

        c = row.createCell(0);
        c.setCellValue("STT");
        c = row.createCell(1);
        c.setCellValue("MSSV");
        c = row.createCell(2);
        c.setCellValue("Họ Tên");
        c = row.createCell(3);
        c.setCellValue("Lớp");
        c = row.createCell(4);
        c.setCellValue("Điểm");

        for (int i = 1; i < mTableLayout.getChildCount(); i++) {
            row = sheet.createRow(i);
            TableRow row_view = (TableRow)mTableLayout.getChildAt(i);
            EditText cell_view;
            String text;

            cell_view = (EditText)row_view.getChildAt(0);
            text = cell_view.getText().toString();
            c = row.createCell(0);
            c.setCellValue(text);

            cell_view = (EditText)row_view.getChildAt(1);
            text = cell_view.getText().toString();
            c = row.createCell(1);
            c.setCellValue(text);

//            cell_view = (EditText)row_view.getChildAt(2);
//            text = cell_view.getText().toString();
//            c = row.createCell(2);
//            c.setCellValue(text);

            c = row.createCell(2);
            c.setCellValue(tableData[i - 1][2]);
            c = row.createCell(3);
            c.setCellValue(tableData[i - 1][3]);


            cell_view = (EditText)row_view.getChildAt(2);
            text = cell_view.getText().toString();
            c = row.createCell(4);
            c.setCellValue(text);
        }
        String fileExt = ".xls";
        String fileName = "table";
        String tempFileName = filepath + "/" + fileName + random.nextInt(100) + fileExt;
        File myExternalFile = new File(Environment.getExternalStorageDirectory(), tempFileName);
        FileOutputStream os;
        try {
            os = new FileOutputStream(myExternalFile);
            wb.write(os);
            os.close();
            return tempFileName;
        } catch (IOException e) {
            Toast.makeText(this, "Cannot write file", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private static boolean isExternalStorageReadOnly() {
        String extStorageState = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED_READ_ONLY.equals(extStorageState);
    }

    private static boolean isExternalStorageAvailable() {
        String extStorageState = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(extStorageState);
    }

}
