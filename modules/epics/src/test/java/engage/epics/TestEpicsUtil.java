/*
 * Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
 * For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause
 */

package engage.epics;

import com.cosylab.epics.caj.cas.util.MemoryProcessVariable;
import gov.aps.jca.dbr.DBRType;
import gov.aps.jca.dbr.DBR_Int;

public class TestEpicsUtil {
    static MemoryProcessVariable createEnumMemoryProcessVariable(String name, short[] init) {
        return new MemoryProcessVariable(name, null, DBRType.ENUM, init);
    }

    static DBR_Int dbrIntValue(Integer v) { return new DBR_Int(new int[] {v}); }
}
