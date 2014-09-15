/*
 * Author:  Filip Dvorak <filip.dvorak@runbox.com>
 *
 * Copyright (c) 2013 Filip Dvorak <filip.dvorak@runbox.com>, all rights reserved
 *
 * Publishing, providing further or using this program is prohibited
 * without previous written permission of the author. Publishing or providing
 * further the contents of this file is prohibited without previous written
 * permission of the author.
 */
package fape.scripting.stncomparison.problem;

import fape.scripting.stncomparison.fullstn.STNManagerOrig;
import planstack.constraints.stn.STNManager;

import java.util.Random;

/**
 *
 * @author FD
 */
public abstract class Event {

    public abstract void Apply(STNManagerOrig o, Random rg);

    public abstract void Apply(STNManager n, Random rg, STNScenario s);

}
