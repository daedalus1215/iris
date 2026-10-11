import { Module } from '@nestjs/common';
import { AppleTvService } from './apple-tv.service';
import { AppleTvController } from './apple-tv.controller';

@Module({
  providers: [AppleTvService],
  controllers: [AppleTvController]
})
export class AppleTvModule {}
