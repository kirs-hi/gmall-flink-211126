package com.atguigu.udf;


import com.alibaba.druid.util.StringUtils;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.annotation.DataTypeHint;
import org.apache.flink.table.annotation.FunctionHint;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import java.io.IOException;
import java.util.List;
import java.io.IOException;

import static org.apache.flink.table.api.Expressions.$;

@FunctionHint(output = @DataTypeHint("ROW<word STRING, length INT>"))
public   class AlienUDTF extends TableFunction<Row> {

    private static final String family = "info";
    private static final String tableName = "product";
    private static final String tableNameSecond = "order_detail";
    private Connection connection;

    public void open(FunctionContext context) throws Exception {
        Configuration conf = HBaseConfiguration.create();
        conf.set("hbase.zookeeper.quorum", "hyjacer");
        //hbase主默认端⼝是60000
        conf.set("hbase.master", "192.168.1.100:60000");
        //zookeeper客户端的端⼝号2181
        conf.set("hbase.zookeeper.property.clientPort", "2181");
        // 添加数据库地址的配置
//conf.setBoolean(DiamondAddressHelper.DIMAOND_HBASE_UNITIZED, true);
//conf.set(DiamondAddressHelper.DIAMOND_HBASE_KEY_NEW,
//        "hbase.diamond.dataid.tms_blink.hbase");
//conf.set(DiamondAddressHelper.DIAMOND_HBASE_GROUP, "hbase-diamond-group-name-tms_blink");
        connection = (Connection) ConnectionFactory.createConnection(conf);
    }

    public void close() throws Exception {
        this.connection.close();
    }

    public void eval(String jsonStr) throws IOException {
        if (StringUtils.isEmpty(jsonStr)) {
            return;
        }

        JSONObject jsonObject = JSON.parseObject(jsonStr);
        if (jsonObject == null) {
            return;
        }
        String rowkey = jsonObject.getString("rk");

        if (StringUtils.isEmpty(rowkey)) {
            return;
        }
        try (Table table = this.connection.getTable(TableName.valueOf(tableName));
             Table tableSecond =
                     this.connection.getTable(TableName.valueOf(tableNameSecond))) {
            //查询第一张表
            Get get = new Get(rowkey.getBytes());
            get.addColumn(family.getBytes(),"product_id".getBytes());
            get.addColumn(family.getBytes(),"in_pirce".getBytes());
            get.addColumn(family.getBytes(),"shop_id".getBytes());
            Result result = table.get(get);
            Row row = new Row(6);

            List<Cell> cells = result.listCells();
            if (CollectionUtils.isEmpty(cells)) {
                System.out.println("query result is empty:" + rowkey);
                return;
            }

            for (Cell cell : cells) {
                String family = Bytes.toString(CellUtil.cloneFamily(cell));
                String column = Bytes.toString(CellUtil.cloneQualifier(cell));
                String value = Bytes.toString(CellUtil.cloneValue(cell));
                System.out.printf("product info:family:%s value:%s column:%s %n",
                        family, value, column);
                if ("product_id".equals(column)) {
                    row.setField(0, Long.valueOf(value));
                } else if ("in_pirce".equals(column)) {
                    row.setField(1, Long.valueOf(value));
                } else if ("shop_id".equals(column)) {
                    row.setField(2, Long.valueOf(value));
                }
            }


            Get getSecond = new Get(rowkey.getBytes());
            get.addColumn(family.getBytes(), "order_id".getBytes());
            get.addColumn(family.getBytes(), "price".getBytes());
            get.addColumn(family.getBytes(), "product_id".getBytes());
            get.addColumn(family.getBytes(), "cnt".getBytes());
            Result resultSecond = tableSecond.get(getSecond);
            List<Cell> cells2 = resultSecond.listCells();
            if(CollectionUtils.isEmpty(cells2)) {
                System.out.println("query result is empty:" + rowkey);
                return;
            }

            for(Cell cell : cells2){
                String family = Bytes.toString(CellUtil.cloneFamily(cell));
                String column = Bytes.toString(CellUtil.cloneQualifier(cell));
                String value = Bytes.toString(CellUtil.cloneValue(cell));
                System.out.printf("product info:family:%s value:%s column:%s %n",
                        family, value, column);
                if ("order_id".equals(column)) {
                    row.setField(3, Long.valueOf(value));
                } else if ("price".equals(column)) {
                    row.setField(4, Long.valueOf(value));
                } else if ("cnt".equals(column)) {
                    row.setField(5, Long.valueOf(value));
                }
            }
            //返回数据
            System.out.println("-----------打印结果----------------------");
            System.out.println("row0:" + row.getField(0)
                    + " row1" + row.getField(1)
                    + " row2" + row.getField(2)
                    + " row3" + row.getField(3)
                    + " row4" + row.getField(4)
                    + " row5" + row.getField(5));
            collect(row);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env =
                StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);
        DataStreamSource<String> input = env.socketTextStream("localhost", 9999);
        tEnv.createTemporaryView("product_json",input,$("jsonstr"));

        tEnv.createTemporarySystemFunction("AlienUDTF",new AlienUDTF());

        org.apache.flink.table.api.Table resultTable = tEnv.sqlQuery(
                "SELECT product_id, in_price, shop_id, order_id, price, cnt "
                        + "FROM product_json "
                        + "LEFT JOIN LATERAL TABLE(AlienUDTF(jsonstr)) AS T(product_id,"
                +"in_price, shop_id,order_id, price, cnt) ON TRUE"
        );
        tEnv.toAppendStream(resultTable, Row.class).print();
        env.execute("UDFApp");

    }




    }




