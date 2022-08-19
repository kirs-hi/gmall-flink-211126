package com.atguigu.udf;



import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.functions.ScalarFunction;
import org.apache.flink.types.Row;
public class MyScalarFunction {
    public static void main(String[] args) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tenv = StreamTableEnvironment.create(env);
        Table table = tenv.fromValues(DataTypes.ROW(
                        DataTypes.FIELD("name", DataTypes.STRING())),
                Row.of("aaa"),
                Row.of("bbb"),
                Row.of("ccc")

        );
        tenv.createTemporaryView("t",table);
        //注册自定义函数
        tenv.createTemporarySystemFunction("upper",MyUpper.class);

        tenv.executeSql("select upper(name) from t").print();
    }

public static class MyUpper extends ScalarFunction{
            public String eval(String str){
                return str.toUpperCase();

            }
        }


    }

