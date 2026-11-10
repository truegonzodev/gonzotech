#!/usr/bin/env python3
"""0.3.338: the old private cache/sentinel API was removed.
Execute the production resumable service, not an extracted emulation of the old cache.
Historical 327/306 contracts remain in Git; scale, progress, stale revocation and
negative chunk-boundary results are exercised by the successor suite.
"""
import runpy
from pathlib import Path
runpy.run_path(str(Path(__file__).with_name('pipe_reform_test.py')), run_name='__main__')
