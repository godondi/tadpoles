package com.neueda.leap.mapper;

import com.neueda.leap.domain.Instrument;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.UpdateProvider;

@Mapper
public interface InstrumentMapper {
    @Select("""
            SELECT instrument_id AS instrumentId,
                   instrument_name AS instrumentName,
                   ticker AS ticker,
                   currency AS currency,
                   asset_class AS assetClass,
                   security_type AS securityType,
                   is_active AS isActive
            FROM instruments
            WHERE instrument_id = #{id}
            """)
    Instrument getInstrument(@Param("id") Integer id);

    @Select("""
            SELECT instrument_id AS instrumentId,
                   instrument_name AS instrumentName,
                   ticker AS ticker,
                   currency AS currency,
                   asset_class AS assetClass,
                   security_type AS securityType,
                   is_active AS isActive
            FROM instruments
            ORDER BY instrument_id
            """)
    List<Instrument> listInstruments();

    @Insert("""
            INSERT INTO instruments (
                instrument_name,
                ticker,
                currency,
                asset_class,
                security_type,
                is_active
            )
            VALUES (
                #{instrumentName},
                #{ticker},
                #{currency},
                #{assetClass},
                #{securityType},
                #{isActive}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "instrumentId", keyColumn = "instrument_id")
    int insertInstrument(Instrument instrument);

    @UpdateProvider(type = InstrumentSqlProvider.class, method = "buildUpdateInstrument")
    int updateInstrument(Instrument instrument);

    class InstrumentSqlProvider {
        public String buildUpdateInstrument(Instrument instrument) {
            List<String> updates = new ArrayList<>();
            if (instrument.getInstrumentName() != null) {
                updates.add("instrument_name = #{instrumentName}");
            }
            if (instrument.getTicker() != null) {
                updates.add("ticker = #{ticker}");
            }
            if (instrument.getCurrency() != null) {
                updates.add("currency = #{currency}");
            }
            if (instrument.getAssetClass() != null) {
                updates.add("asset_class = #{assetClass}");
            }
            if (instrument.getSecurityType() != null) {
                updates.add("security_type = #{securityType}");
            }
            if (instrument.getIsActive() != null) {
                updates.add("is_active = #{isActive}");
            }
            return "UPDATE instruments SET "
                    + String.join(", ", updates)
                    + " WHERE instrument_id = #{instrumentId}";
        }
    }
}
